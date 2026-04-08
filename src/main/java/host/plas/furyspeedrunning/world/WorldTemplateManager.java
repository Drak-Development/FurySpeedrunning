package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.MainConfig;
import host.plas.furyspeedrunning.data.GameManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Material;
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

public class WorldTemplateManager {
    private static final String TEMPLATE_DIR_NAME = "templates";

    @Getter
    private static boolean generating = false;

    @Getter
    private static String generationStatus = "";

    // Tracks current Chunky pre-gen polling task
    private static int chunkyPollTaskId = -1;

    // Queue of seeds with ready templates
    private static final Deque<Long> readyQueue = new ArrayDeque<>();

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

    // --- World Queue System ---

    public static int getQueueSize() {
        return readyQueue.size();
    }

    /**
     * Starts the world queue system. Loads existing templates and begins
     * generating new ones until the queue target is reached.
     */
    public static void startWorldQueue() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        // Clean up any interrupted template generations from previous runs
        cleanupInterruptedGenerations();

        // Load existing templates into queue
        File templatesDir = getTemplatesDir();
        File[] seedDirs = templatesDir.listFiles(File::isDirectory);
        if (seedDirs != null) {
            for (File dir : seedDirs) {
                try {
                    long seed = Long.parseLong(dir.getName());
                    if (hasTemplate(seed)) {
                        readyQueue.add(seed);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        int target = 5;
        plugin.logInfo("&a" + readyQueue.size() + "/" + target + " world templates loaded into queue.");

        fillQueue();
    }

    /**
     * Fills the queue to the target size by finding FSG seeds and generating templates.
     * Generates one template at a time.
     */
    private static void fillQueue() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        int target = 5;
        if (readyQueue.size() >= target || generating) return;

        generating = true;
        generationStatus = "Finding FSG seed...";

        // Find a valid FSG seed off the main thread
        java.util.Set<Long> played = java.util.Collections.emptySet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long seed;
            // Keep searching until we find a seed that hasn't been played
            do {
                seed = SeedValidator.findFilteredSeed();
            } while (played.contains(seed));

            long finalSeed = seed;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (hasTemplate(finalSeed) || readyQueue.contains(finalSeed)) {
                    // Already have this seed — add to queue if not already there
                    if (!readyQueue.contains(finalSeed) && hasTemplate(finalSeed)) {
                        readyQueue.add(finalSeed);
                    }
                    generating = false;
                    generationStatus = "";
                    fillQueue();
                } else {
                    plugin.logInfo("&eFSG seed found: &b" + finalSeed + " &e— generating template...");
                    generateTemplate(finalSeed, () -> {
                        readyQueue.add(finalSeed);
                        generating = false;
                        generationStatus = "";
                        int current = readyQueue.size();
                        plugin.logInfo("&aWorld template queued for seed &e" + finalSeed + " &a(" + current + "/" + target + ")");
                        fillQueue();
                    });
                }
            });
        });
    }

    /**
     * Takes the next ready seed from the queue for use in a game, skipping already-played seeds.
     * Deletes the used template and triggers generation of a replacement.
     */
    public static Long pollReadySeed(java.util.Set<Long> playedSeeds) {
        Long seed = null;
        while (!readyQueue.isEmpty()) {
            Long candidate = readyQueue.poll();
            if (candidate != null && !playedSeeds.contains(candidate)) {
                seed = candidate;
                break;
            }
            // This seed was already played — delete its template
            if (candidate != null) {
                deleteTemplateAsync(candidate);
            }
        }
        if (seed != null) {
            // Delete the template we're about to use so it won't be re-queued
            deleteTemplateAsync(seed);
        }
        // Start generating a replacement after a short delay
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> fillQueue(), 20L);
        return seed;
    }

    /**
     * Deletes a template's files asynchronously.
     */
    private static void deleteTemplateAsync(long seed) {
        Bukkit.getScheduler().runTaskAsynchronously(FurySpeedrunning.getInstance(), () -> {
            File seedDir = getSeedDir(seed);
            if (seedDir.exists()) {
                deleteRecursive(seedDir);
                FurySpeedrunning.getInstance().logInfo("&aDeleted used template for seed: " + seed);
            }
        });
    }

    // --- Template Generation ---

    private static void generateTemplate(long seed, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String prefix = "template_gen_" + seed;
        MainConfig config = FurySpeedrunning.getMainConfig();
        int radiusBlocks = config.getPreGenRadius();

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

        // Apply structure spacing modifications before chunk generation
        WorldGenModifier.modifyStructureSpacing(overworld);
        WorldGenModifier.modifyStructureSpacing(netherWorld);
        WorldGenModifier.modifyStructureSpacing(endWorld);

        int netherRadius = Math.max(radiusBlocks / 8, 256);
        int endRadius = Math.max(radiusBlocks / 4, 192);

        // Chain: overworld → blacksmith check → nether → end → save → cleanup
        generationStatus = "Seed " + seed + " \u2014 overworld (" + radiusBlocks + " blocks)";
        chunkyPreGen(overworld, radiusBlocks, () -> {
            // After overworld gen, verify village has a blacksmith
            if (!hasBlacksmithNearVillage(overworld, seed)) {
                plugin.logWarning("Seed " + seed + " has no blacksmith in village — discarding.");
                Bukkit.unloadWorld(overworld, false);
                Bukkit.unloadWorld(netherWorld, false);
                Bukkit.unloadWorld(endWorld, false);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_overworld"));
                    deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_nether"));
                    deleteRecursive(new File(Bukkit.getWorldContainer(), prefix + "_the_end"));
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        generating = false;
                        generationStatus = "";
                        fillQueue(); // Retry with a new seed
                    });
                });
                return;
            }
            plugin.logInfo("&aBlacksmith confirmed for seed &b" + seed);

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
     * Scans the area around the expected village position for blacksmith indicator blocks.
     * Checks for BLAST_FURNACE (weaponsmith) and SMITHING_TABLE (toolsmith) which only
     * appear in village blacksmith buildings.
     */
    private static boolean hasBlacksmithNearVillage(World overworld, long seed) {
        int[] villageChunks = SeedValidator.getVillageChunks(seed);
        int scanRadius = 5; // chunks around village center

        for (int cx = villageChunks[0] - scanRadius; cx <= villageChunks[0] + scanRadius; cx++) {
            for (int cz = villageChunks[1] - scanRadius; cz <= villageChunks[1] + scanRadius; cz++) {
                Chunk chunk = overworld.getChunkAt(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 55; y < 80; y++) {
                            Material type = chunk.getBlock(x, y, z).getType();
                            if (type == Material.BLAST_FURNACE || type == Material.SMITHING_TABLE) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
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
     */
    private static boolean isChunkyRunningForWorld(String worldName) {
        try {
            var provider = Bukkit.getServicesManager().getRegistration(
                    Class.forName("org.popcraft.chunky.api.ChunkyAPI")
            );
            if (provider != null) {
                Object api = provider.getProvider();
                var world = Bukkit.getWorld(worldName);
                if (world != null) {
                    var method = api.getClass().getMethod("isRunning", World.class);
                    return (boolean) method.invoke(api, world);
                }
            }
        } catch (Exception ignored) {
        }
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
