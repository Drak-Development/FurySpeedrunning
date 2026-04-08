package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses NMS reflection to modify structure spacing in game worlds.
 * Reduces spacing/separation values so structures generate closer together.
 * Falls back gracefully if NMS classes are unavailable or changed.
 */
public class WorldGenModifier {

    // Structure name -> {spacing, separation} (vanilla defaults reduced ~50%)
    // Keep numeric values in sync with SeedValidator (structure placement math for FSG-style checks).
    private static final Map<String, int[]> TARGET_SPACING = new HashMap<>();
    static {
        // Vanilla -> Modified (tighter for speedrunning)
        TARGET_SPACING.put("village",          new int[]{16, 4});   // was 32, 8
        TARGET_SPACING.put("fortress",         new int[]{15, 3});   // was 27, 4
        TARGET_SPACING.put("bastion_remnant",  new int[]{15, 3});   // was 27, 4
        TARGET_SPACING.put("ruined_portal",    new int[]{20, 6});   // was 40, 15
        TARGET_SPACING.put("desert_pyramid",   new int[]{16, 4});   // was 32, 8
        TARGET_SPACING.put("jungle_pyramid",   new int[]{16, 4});   // was 32, 8
        TARGET_SPACING.put("shipwreck",        new int[]{14, 4});   // was 24, 4
        TARGET_SPACING.put("buried_treasure",  new int[]{1, 0});    // was 1, 0 (keep same)
        TARGET_SPACING.put("endcity",          new int[]{12, 6});   // was 20, 11
    }

    /**
     * Attempts to modify the structure spacing of a world's chunk generator via NMS reflection.
     * This must be called after world creation but before chunks are generated.
     */
    public static void modifyStructureSpacing(World world) {
        try {
            String version = getServerVersion();
            if (version == null) {
                FurySpeedrunning.getInstance().logWarning("Could not detect NMS version for worldgen modification.");
                return;
            }

            // CraftWorld -> WorldServer
            Object nmsWorld = world.getClass().getMethod("getHandle").invoke(world);

            // WorldServer -> ChunkProviderServer
            Object chunkProvider = invokeMethod(nmsWorld, "getChunkProvider");
            if (chunkProvider == null) {
                // Try alternative method names
                chunkProvider = getFieldByTypeName(nmsWorld, "ChunkProviderServer");
            }
            if (chunkProvider == null) return;

            // ChunkProviderServer -> ChunkGenerator
            Object chunkGenerator = getFieldByTypeName(chunkProvider, "ChunkGenerator");
            if (chunkGenerator == null) return;

            // Check if this is a ChunkGeneratorAbstract (normal terrain generator)
            if (!chunkGenerator.getClass().getSimpleName().contains("ChunkGenerator")) return;

            // Find the structure settings — could be in the generator or in its noise settings
            Object structureSettings = findStructureSettings(chunkGenerator);
            if (structureSettings == null) {
                FurySpeedrunning.getInstance().logWarning("Could not locate structure settings in chunk generator.");
                return;
            }

            // Find the Map<StructureGenerator, StructureSpacing> field
            modifySpacingMap(structureSettings, version);

            FurySpeedrunning.getInstance().logInfo("&aModified structure spacing for world: &e" + world.getName());

        } catch (Exception e) {
            FurySpeedrunning.getInstance().logWarning(
                    "Could not modify structure spacing for " + world.getName() + ": " + e.getMessage()
            );
        }
    }

    private static String getServerVersion() {
        try {
            String packageName = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            // org.bukkit.craftbukkit.v1_16_R1 -> v1_16_R1
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeMethod(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            // Try declared methods too
            try {
                Method method = obj.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static Object getFieldByTypeName(Object obj, String typeNamePart) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType().getSimpleName().contains(typeNamePart)) {
                    f.setAccessible(true);
                    try {
                        return f.get(obj);
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Returns true if the class is a primitive JDK type that should never be traversed.
     * Allows Supplier, Map, and other container types through.
     */
    private static boolean shouldSkipField(Class<?> fieldType, Object value) {
        if (fieldType == null) return true;
        // Always allow: Supplier (wraps NMS settings), Map (structure map), and NMS types
        if (java.util.function.Supplier.class.isAssignableFrom(fieldType)) return false;
        if (Map.class.isAssignableFrom(fieldType)) return false;
        // Skip primitive wrappers and common JDK value types that can't contain NMS data
        if (fieldType == int.class || fieldType == long.class || fieldType == boolean.class
                || fieldType == float.class || fieldType == double.class
                || fieldType == byte.class || fieldType == short.class || fieldType == char.class) return true;
        if (fieldType == Integer.class || fieldType == Long.class || fieldType == Boolean.class
                || fieldType == Float.class || fieldType == Double.class
                || fieldType == String.class || fieldType == Class.class) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object findStructureSettings(Object chunkGenerator) {
        // Walk the generator's fields looking for structure settings
        // In 1.16.1, ChunkGeneratorAbstract has a Supplier<GeneratorSettingBase>
        // GeneratorSettingBase contains StructureSettingsFeature

        Class<?> clazz = chunkGenerator.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (shouldSkipField(f.getType(), null)) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(chunkGenerator);
                    if (val == null) continue;

                    // Check if it's a Supplier
                    if (val instanceof java.util.function.Supplier) {
                        Object supplied = ((java.util.function.Supplier<?>) val).get();
                        if (supplied != null) {
                            // Look for structure settings inside the supplied object
                            Object settings = findFieldWithMapOfStructures(supplied);
                            if (settings != null) return settings;
                        }
                    }

                    // Check if the field itself contains a map of structures
                    if (hasMapOfStructures(val)) {
                        return val;
                    }

                    // Check nested NMS objects for structure settings
                    String valClassName = val.getClass().getName();
                    if (!valClassName.startsWith("java.") && !valClassName.startsWith("javax.")) {
                        Object nested = findFieldWithMapOfStructures(val);
                        if (nested != null) return nested;
                    }

                } catch (Exception e) {
                    // Skip inaccessible fields
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Object findFieldWithMapOfStructures(Object obj) {
        if (obj == null) return null;
        String objClassName = obj.getClass().getName();
        if (objClassName.startsWith("java.") && !Map.class.isAssignableFrom(obj.getClass())) return null;
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(obj);
                if (hasMapOfStructures(val)) {
                    return obj;
                }
            } catch (Exception e) {
                // Skip
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasMapOfStructures(Object obj) {
        if (!(obj instanceof Map)) return false;
        Map<?, ?> map = (Map<?, ?>) obj;
        if (map.isEmpty()) return false;
        // Check if keys look like structure generators
        Object firstKey = map.keySet().iterator().next();
        return firstKey != null && firstKey.getClass().getSimpleName().contains("StructureGenerator");
    }

    @SuppressWarnings("unchecked")
    private static void modifySpacingMap(Object structureSettings, String version) throws Exception {
        // Find the map field containing StructureGenerator -> spacing
        for (Field f : structureSettings.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object val = f.get(structureSettings);
            if (!(val instanceof Map)) continue;
            Map<Object, Object> spacingMap = (Map<Object, Object>) val;
            if (spacingMap.isEmpty()) continue;

            // Verify this is the right map
            Object firstKey = spacingMap.keySet().iterator().next();
            if (firstKey == null || !firstKey.getClass().getSimpleName().contains("StructureGenerator")) continue;

            // Make the map mutable (it might be immutable)
            Map<Object, Object> mutableMap = new HashMap<>(spacingMap);

            // Find the spacing class constructor
            Object firstValue = spacingMap.values().iterator().next();
            Class<?> spacingClass = firstValue.getClass();
            Constructor<?> spacingConstructor = findSpacingConstructor(spacingClass);
            if (spacingConstructor == null) {
                FurySpeedrunning.getInstance().logWarning("Could not find spacing constructor.");
                return;
            }

            // Get the structure name for each key
            for (Map.Entry<Object, Object> entry : new HashMap<>(mutableMap).entrySet()) {
                String structureName = getStructureName(entry.getKey());
                if (structureName == null) continue;

                int[] target = TARGET_SPACING.get(structureName);
                if (target == null) continue;

                // Get the existing salt
                int salt = getSpacingSalt(entry.getValue());

                // Create new spacing with reduced values
                Object newSpacing = spacingConstructor.newInstance(target[0], target[1], salt);
                mutableMap.put(entry.getKey(), newSpacing);
            }

            // Replace the map in the field
            f.set(structureSettings, mutableMap);
            return;
        }
    }

    private static Constructor<?> findSpacingConstructor(Class<?> spacingClass) {
        for (Constructor<?> c : spacingClass.getDeclaredConstructors()) {
            c.setAccessible(true);
            Class<?>[] params = c.getParameterTypes();
            // Looking for (int spacing, int separation, int salt)
            if (params.length == 3
                    && params[0] == int.class
                    && params[1] == int.class
                    && params[2] == int.class) {
                return c;
            }
        }
        return null;
    }

    private static String getStructureName(Object structureGenerator) {
        // Try to get the name via toString or a name field
        try {
            // StructureGenerator often has a getName() or similar
            for (Method m : structureGenerator.getClass().getMethods()) {
                if (m.getReturnType() == String.class && m.getParameterCount() == 0) {
                    String name = (String) m.invoke(structureGenerator);
                    if (name != null && !name.isEmpty() && !name.contains("@")) {
                        return name.toLowerCase();
                    }
                }
            }
        } catch (Exception e) {
            // Fall through
        }

        // Try toString
        String str = structureGenerator.toString().toLowerCase();
        for (String key : TARGET_SPACING.keySet()) {
            if (str.contains(key)) return key;
        }
        return null;
    }

    private static int getSpacingSalt(Object spacing) {
        // The salt is typically the 3rd int field
        try {
            Field[] fields = spacing.getClass().getDeclaredFields();
            int intCount = 0;
            for (Field f : fields) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    intCount++;
                    if (intCount == 3) { // 3rd int field = salt
                        return f.getInt(spacing);
                    }
                }
            }
        } catch (Exception e) {
            // Return a default salt
        }
        return 0;
    }
}
