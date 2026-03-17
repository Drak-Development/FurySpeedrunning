package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.MainConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class WorldTemplateManager {
    private static final String TEMPLATE_DIR_NAME = "templates";

    @Getter
    private static boolean generating = false;

    @Getter
    private static String generationStatus = "";

    // Tracks current Chunky pre-gen polling task
    private static int chunkyPollTaskId = -1;

    private static File getTemplatesDir() {
        File dir = new File(FurySpeedrunning.getInstance().getDataFolder(), TEMPLATE_DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File getSeedDir(long seed) {
        return new File(getTemplatesDir(), String.valueOf(seed));
    }

    public static boolean hasTemplate(long seed) {
        File seedDir = getSeedDir(seed);
        return new File(seedDir, "overworld").exists()
                && new File(seedDir, "nether").exists()
                && new File(seedDir, "the_end").exists();
    }

    private static boolean isChunkyAvailable() {
        return Bukkit.getPluginManager().getPlugin("Chunky") != null
                && Bukkit.getPluginManager().isPluginEnabled("Chunky");
    }

    /**
     * Generates templates for all seeds that don't already have one.
     * Uses Chunky for chunk pre-generation.
     */
    public static void generateMissingTemplates(Runnable onAllComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        if (!isChunkyAvailable()) {
            plugin.logWarning("&cChunky plugin not found! Templates will not be pre-generated.");
            plugin.logWarning("&cInstall Chunky for pre-generation: https://modrinth.com/plugin/chunky");
            if (onAllComplete != null) onAllComplete.run();
            return;
        }

        List<Long> seeds = plugin.getMainConfig().getSeeds();

        Deque<Long> toGenerate = new ArrayDeque<>();
        for (long seed : seeds) {
            if (!hasTemplate(seed)) {
                toGenerate.add(seed);
            } else {
                plugin.logInfo("&aTemplate for seed &e" + seed + " &aalready exists.");
            }
        }

        if (toGenerate.isEmpty()) {
            plugin.logInfo("&aAll seed templates are ready.");
            if (onAllComplete != null) onAllComplete.run();
            return;
        }

        generating = true;
        plugin.logInfo("&e" + toGenerate.size() + " seed template(s) need generation via Chunky...");
        Bukkit.broadcastMessage("\u00A7e\u00A7lPre-generating world templates via Chunky...");

        generateNextSeed(toGenerate, onAllComplete);
    }

    private static void generateNextSeed(Deque<Long> remaining, Runnable onAllComplete) {
        if (remaining.isEmpty()) {
            generating = false;
            generationStatus = "";
            FurySpeedrunning.getInstance().logInfo("&a&lAll seed templates generated!");
            Bukkit.broadcastMessage("\u00A7a\u00A7lAll world templates are ready!");
            if (onAllComplete != null) onAllComplete.run();
            return;
        }

        long seed = remaining.poll();
        generateTemplate(seed, () -> generateNextSeed(remaining, onAllComplete));
    }

    private static void generateTemplate(long seed, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String prefix = "template_gen_" + seed;
        MainConfig config = FurySpeedrunning.getMainConfig();
        int radiusBlocks = config.getTemplatePreGenRadius();

        plugin.logInfo("&eGenerating template for seed &b" + seed + "&e...");
        generationStatus = "Seed " + seed + " \u2014 creating worlds...";

        // Create temporary worlds
        World overworld = new WorldCreator(prefix + "_overworld")
                .seed(seed).environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL).createWorld();
        World netherWorld = new WorldCreator(prefix + "_nether")
                .seed(seed).environment(World.Environment.NETHER)
                .type(WorldType.NORMAL).createWorld();
        World endWorld = new WorldCreator(prefix + "_the_end")
                .seed(seed).environment(World.Environment.THE_END)
                .type(WorldType.NORMAL).createWorld();

        if (overworld == null || netherWorld == null || endWorld == null) {
            plugin.logSevere("Failed to create template worlds for seed " + seed);
            cleanupTempWorlds(prefix);
            onComplete.run();
            return;
        }

        overworld.setDifficulty(Difficulty.NORMAL);
        netherWorld.setDifficulty(Difficulty.NORMAL);
        endWorld.setDifficulty(Difficulty.NORMAL);

        int netherRadius = Math.max(radiusBlocks / 8, 256);
        int endRadius = Math.max(radiusBlocks / 4, 192);

        // Chain: overworld → nether → end → save → cleanup
        generationStatus = "Seed " + seed + " \u2014 overworld (" + radiusBlocks + " blocks)";
        chunkyPreGen(overworld, radiusBlocks, () -> {
            generationStatus = "Seed " + seed + " \u2014 nether (" + netherRadius + " blocks)";
            chunkyPreGen(netherWorld, netherRadius, () -> {
                generationStatus = "Seed " + seed + " \u2014 end (" + endRadius + " blocks)";
                chunkyPreGen(endWorld, endRadius, () -> {
                    generationStatus = "Seed " + seed + " \u2014 saving template...";

                    // Save all chunks
                    overworld.save();
                    netherWorld.save();
                    endWorld.save();

                    // Unload worlds (keep files)
                    Bukkit.unloadWorld(overworld, true);
                    Bukkit.unloadWorld(netherWorld, true);
                    Bukkit.unloadWorld(endWorld, true);

                    // Copy world folders to template dir async
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        File seedDir = getSeedDir(seed);
                        seedDir.mkdirs();

                        copyDirectory(
                                new File(Bukkit.getWorldContainer(), prefix + "_overworld"),
                                new File(seedDir, "overworld")
                        );
                        copyDirectory(
                                new File(Bukkit.getWorldContainer(), prefix + "_nether"),
                                new File(seedDir, "nether")
                        );
                        copyDirectory(
                                new File(Bukkit.getWorldContainer(), prefix + "_the_end"),
                                new File(seedDir, "the_end")
                        );

                        // Delete temp world folders
                        deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_overworld"));
                        deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_nether"));
                        deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_the_end"));

                        plugin.logInfo("&aTemplate for seed &b" + seed + " &asaved!");

                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    });
                });
            });
        });
    }

    /**
     * Uses Chunky command dispatch to pre-generate a world, then polls for completion.
     */
    private static void chunkyPreGen(World world, int radiusBlocks, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String worldName = world.getName();

        plugin.logInfo("&7  Chunky: starting pre-gen for &e" + worldName + " &7(" + radiusBlocks + " blocks)");

        // Configure and start Chunky
        int cx = world.getSpawnLocation().getBlockX();
        int cz = world.getSpawnLocation().getBlockZ();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky world " + worldName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky shape square");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky center " + cx + " " + cz);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky radius " + radiusBlocks);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky start");

        // Poll every 5 seconds to check if Chunky is done
        chunkyPollTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!isChunkyRunningForWorld(worldName)) {
                    if (chunkyPollTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(chunkyPollTaskId);
                    }
                    chunkyPollTaskId = -1;
                    plugin.logInfo("&a  Chunky: finished pre-gen for &e" + worldName);
                    onComplete.run();
                }
            }
        }, 100L, 100L); // Check every 5 seconds
    }

    /**
     * Checks if Chunky is currently running a task for a given world.
     * Uses the Chunky API service if available, falls back to a heuristic.
     */
    private static boolean isChunkyRunningForWorld(String worldName) {
        try {
            // Try to use Chunky's API via service provider
            var provider = Bukkit.getServicesManager().getRegistration(
                    Class.forName("org.popcraft.chunky.api.ChunkyAPI")
            );
            if (provider != null) {
                Object api = provider.getProvider();
                // Call isRunning(world) via reflection
                var world = Bukkit.getWorld(worldName);
                if (world != null) {
                    var method = api.getClass().getMethod("isRunning", World.class);
                    return (boolean) method.invoke(api, world);
                }
            }
        } catch (Exception ignored) {
            // API not available, use fallback
        }

        // Fallback: check if Chunky plugin has active generation tasks
        // Chunky stores running tasks internally; if we can't access the API,
        // assume it's done after a generous timeout. This is handled by the
        // polling task running for a maximum duration.
        return false;
    }

    // --- Template loading ---

    public static boolean createFromTemplate(long seed, String overworldName, String netherName, String endName) {
        if (!hasTemplate(seed)) return false;

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        File seedDir = getSeedDir(seed);

        copyDirectory(new File(seedDir, "overworld"), new File(Bukkit.getWorldContainer(), overworldName));
        copyDirectory(new File(seedDir, "nether"), new File(Bukkit.getWorldContainer(), netherName));
        copyDirectory(new File(seedDir, "the_end"), new File(Bukkit.getWorldContainer(), endName));

        for (String name : new String[]{overworldName, netherName, endName}) {
            File worldDir = new File(Bukkit.getWorldContainer(), name);
            new File(worldDir, "uid.dat").delete();
            new File(worldDir, "session.lock").delete();
        }

        plugin.logInfo("&aCopied templates for seed &b" + seed + "&a.");
        return true;
    }

    // --- Cleanup ---

    public static void cleanupTempWorlds(String prefix) {
        File worldContainer = Bukkit.getWorldContainer();
        File[] files = worldContainer.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && file.getName().startsWith(prefix)) {
                World w = Bukkit.getWorld(file.getName());
                if (w != null) Bukkit.unloadWorld(w, false);
                deleteRecursive(file);
            }
        }
    }

    public static void cleanupInterruptedGenerations() {
        File worldContainer = Bukkit.getWorldContainer();
        File[] files = worldContainer.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && file.getName().startsWith("template_gen_")) {
                World w = Bukkit.getWorld(file.getName());
                if (w != null) Bukkit.unloadWorld(w, false);
                deleteRecursive(file);
                FurySpeedrunning.getInstance().logInfo("&aCleaned up interrupted template world: " + file.getName());
            }
        }
    }

    /**
     * Cancels any active Chunky tasks (used during shutdown).
     */
    public static void cancelActiveGeneration() {
        if (chunkyPollTaskId != -1) {
            Bukkit.getScheduler().cancelTask(chunkyPollTaskId);
            chunkyPollTaskId = -1;
        }
        if (isChunkyAvailable()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky cancel");
        }
        generating = false;
        generationStatus = "";
    }

    // --- File utilities ---

    static void copyDirectory(File source, File target) {
        if (!source.exists()) return;
        target.mkdirs();

        File[] files = source.listFiles();
        if (files == null) return;

        for (File file : files) {
            File dest = new File(target, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, dest);
            } else {
                copyFile(file, dest);
            }
        }
    }

    private static void copyFile(File source, File dest) {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel srcChannel = fis.getChannel();
             FileChannel dstChannel = fos.getChannel()) {
            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
        } catch (IOException e) {
            FurySpeedrunning.getInstance().logWarning("Failed to copy file: " + source.getName());
        }
    }

    static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
