package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldManager {
    @Getter
    private static World overworld;
    @Getter
    private static World nether;
    @Getter
    private static World end;

    private static String currentPrefix;

    public static void createGameWorlds(long seed) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        currentPrefix = plugin.getMainConfig().getWorldPrefix() + "_" + System.currentTimeMillis();

        overworld = new WorldCreator(currentPrefix + "_overworld")
                .seed(seed)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .createWorld();

        nether = new WorldCreator(currentPrefix + "_nether")
                .seed(seed)
                .environment(World.Environment.NETHER)
                .type(WorldType.NORMAL)
                .createWorld();

        end = new WorldCreator(currentPrefix + "_the_end")
                .seed(seed)
                .environment(World.Environment.THE_END)
                .type(WorldType.NORMAL)
                .createWorld();

        if (overworld != null) {
            overworld.setDifficulty(Difficulty.NORMAL);
            setBooleanRule(overworld, "announceAdvancements", false);
        }
        if (nether != null) {
            nether.setDifficulty(Difficulty.NORMAL);
            setBooleanRule(nether, "announceAdvancements", false);
        }
        if (end != null) {
            end.setDifficulty(Difficulty.NORMAL);
            setBooleanRule(end, "announceAdvancements", false);
        }

        plugin.logInfo("&aGame worlds created: " + currentPrefix);
    }

    @SuppressWarnings({"unchecked", "removal"})
    private static void setBooleanRule(World world, String ruleName, boolean value) {
        GameRule<?> rule = GameRule.getByName(ruleName);
        if (rule != null) {
            world.setGameRule((GameRule<Boolean>) rule, value);
        }
    }

    public static void preGenerateChunks(World world, int radius, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        Location spawn = world.getSpawnLocation();
        int centerX = spawn.getBlockX() >> 4;
        int centerZ = spawn.getBlockZ() >> 4;

        // Build flat list of all chunk coords
        List<int[]> chunks = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                chunks.add(new int[]{x, z});
            }
        }

        int totalChunks = chunks.size();
        if (totalChunks == 0) {
            onComplete.run();
            return;
        }

        AtomicInteger loaded = new AtomicInteger(0);
        int batchSize = 4;

        // Process chunks in batches using a repeating task on the main thread
        // Each tick processes a batch, so we don't freeze the server
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + batchSize, totalChunks);
            for (int i = index[0]; i < end; i++) {
                int[] coord = chunks.get(i);
                world.getChunkAt(coord[0], coord[1]).load(true);
                loaded.incrementAndGet();
            }
            index[0] = end;

            if (index[0] >= totalChunks) {
                task.cancel();
                plugin.logInfo("&aPre-generated " + totalChunks + " chunks.");
                onComplete.run();
            }
        }, 1L, 1L);
    }

    public static void deleteGameWorlds() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        if (overworld != null) {
            String name = overworld.getName();
            Bukkit.unloadWorld(overworld, false);
            overworld = null;
            deleteWorldFolderAsync(name);
        }

        if (nether != null) {
            String name = nether.getName();
            Bukkit.unloadWorld(nether, false);
            nether = null;
            deleteWorldFolderAsync(name);
        }

        if (end != null) {
            String name = end.getName();
            Bukkit.unloadWorld(end, false);
            end = null;
            deleteWorldFolderAsync(name);
        }

        currentPrefix = null;
    }

    public static boolean isGameWorld(World world) {
        if (world == null) return false;
        return world.equals(overworld) || world.equals(nether) || world.equals(end);
    }

    public static void deleteWorldFolder(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteRecursive(worldFolder);
        }
    }

    private static void deleteWorldFolderAsync(String worldName) {
        Bukkit.getScheduler().runTaskAsynchronously(FurySpeedrunning.getInstance(), () -> {
            deleteWorldFolder(worldName);
            FurySpeedrunning.getInstance().logInfo("&aDeleted world folder: " + worldName);
        });
    }

    private static void deleteRecursive(File file) {
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

    public static void cleanupStaleWorlds() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String prefix = plugin.getMainConfig().getWorldPrefix() + "_";
        File worldContainer = Bukkit.getWorldContainer();
        File[] files = worldContainer.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && file.getName().startsWith(prefix)) {
                World world = Bukkit.getWorld(file.getName());
                if (world != null) {
                    Bukkit.unloadWorld(world, false);
                }
                deleteRecursive(file);
                plugin.logInfo("&aCleaned up stale world: " + file.getName());
            }
        }
    }
}
