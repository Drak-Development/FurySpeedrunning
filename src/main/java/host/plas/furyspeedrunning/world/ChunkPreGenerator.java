package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkPreGenerator {
    private static final int PAPER_BATCH_SIZE = 30;

    private final BossBar bossBar;
    private int totalChunks = 0;
    private BukkitTask progressTask;
    private BukkitTask batchTask;

    @Getter
    private boolean running = false;

    // Paper async mode
    private final AtomicInteger paperGenerated = new AtomicInteger(0);

    // Chunky mode
    private final List<World> chunkyWorlds = new ArrayList<>();
    private long expectedRegionBytes;

    public ChunkPreGenerator() {
        bossBar = Bukkit.createBossBar(
                "\u00A7ePreparing world...",
                BarColor.GREEN,
                BarStyle.SOLID
        );
        bossBar.setProgress(0.0);
    }

    public void generate(List<GenTask> tasks, Runnable onComplete) {
        running = true;
        for (GenTask t : tasks) {
            totalChunks += t.chunkCoords.size();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        if (isChunkyAvailable()) {
            generateWithChunky(tasks, onComplete);
        } else {
            generateWithPaper(tasks, onComplete);
        }
    }

    // ======== Chunky mode ========

    private boolean isChunkyAvailable() {
        return Bukkit.getPluginManager().getPlugin("Chunky") != null
                && Bukkit.getPluginManager().isPluginEnabled("Chunky");
    }

    private void generateWithChunky(List<GenTask> tasks, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        // ~3KB per chunk average in region files
        expectedRegionBytes = (long) totalChunks * 3000L;

        for (GenTask task : tasks) {
            World world = task.world;
            chunkyWorlds.add(world);
            int radius = task.radiusBlocks;
            int cx = 0;
            int cz = 0;

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky world " + world.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky shape square");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky center " + cx + " " + cz);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky radius " + radius);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky start");

            plugin.logInfo("&7Chunky: generating &e" + world.getName() + " &7(" + radius + " block radius)");
        }

        // Poll progress via region file sizes
        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long totalSize = 0;
            for (World w : chunkyWorlds) {
                totalSize += getRegionDirSize(w);
            }

            double pct = Math.min(0.99, (double) totalSize / expectedRegionBytes);
            int estimatedDone = (int) (pct * totalChunks);
            bossBar.setProgress(pct);
            bossBar.setTitle("\u00A7e" + estimatedDone + "/" + totalChunks + " chunks generated (" + (int) (pct * 100) + "%)");

            // Check if all Chunky tasks are done
            boolean allDone = true;
            for (World w : chunkyWorlds) {
                if (isChunkyRunning(w.getName())) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                bossBar.setProgress(1.0);
                bossBar.setTitle("\u00A7e" + totalChunks + "/" + totalChunks + " chunks generated (100%)");
                cleanup();
                onComplete.run();
            }
        }, 40L, 40L); // Every 2 seconds
    }

    private long getRegionDirSize(World world) {
        File regionDir;
        switch (world.getEnvironment()) {
            case NETHER:
                regionDir = new File(world.getWorldFolder(), "DIM-1/region");
                break;
            case THE_END:
                regionDir = new File(world.getWorldFolder(), "DIM1/region");
                break;
            default:
                regionDir = new File(world.getWorldFolder(), "region");
        }
        if (!regionDir.exists()) return 0;
        File[] files = regionDir.listFiles((d, n) -> n.endsWith(".mca"));
        if (files == null) return 0;
        long total = 0;
        for (File f : files) total += f.length();
        return total;
    }

    private boolean isChunkyRunning(String worldName) {
        try {
            Object provider = Bukkit.getServicesManager().getRegistration(
                    Class.forName("org.popcraft.chunky.api.ChunkyAPI")
            );
            if (provider != null) {
                Object api = ((org.bukkit.plugin.RegisteredServiceProvider<?>) provider).getProvider();
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    var method = api.getClass().getMethod("isRunning", World.class);
                    return (boolean) method.invoke(api, world);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ======== Paper async mode (fallback) ========

    private void generateWithPaper(List<GenTask> tasks, Runnable onComplete) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        plugin.logInfo("&7Chunky not found, using async chunk generation...");

        Queue<QueuedChunk> allChunks = new ArrayDeque<>();
        for (GenTask t : tasks) {
            for (int[] coord : t.chunkCoords) {
                allChunks.add(new QueuedChunk(t.world, coord[0], coord[1]));
            }
        }

        // Dispatch batches of async chunk requests
        batchTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < PAPER_BATCH_SIZE && !allChunks.isEmpty(); i++) {
                QueuedChunk qc = allChunks.poll();
                qc.world.getChunkAtAsync(qc.x, qc.z, true).thenRun(() -> {
                    paperGenerated.incrementAndGet();
                });
            }
            if (allChunks.isEmpty()) {
                batchTask.cancel();
                batchTask = null;
            }
        }, 1L, 1L);

        // Progress display
        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int done = paperGenerated.get();
            double pct = (double) done / totalChunks;
            bossBar.setProgress(Math.min(1.0, pct));
            bossBar.setTitle("\u00A7e" + done + "/" + totalChunks + " chunks generated (" + (int) (pct * 100) + "%)");

            if (done >= totalChunks) {
                cleanup();
                onComplete.run();
            }
        }, 10L, 10L);
    }

    // ======== Common ========

    public void addPlayer(Player player) {
        if (running) bossBar.addPlayer(player);
    }

    public void cancel() {
        // Cancel Chunky if running
        if (!chunkyWorlds.isEmpty() && isChunkyAvailable()) {
            for (World w : chunkyWorlds) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky cancel");
                break; // Chunky cancel stops all tasks
            }
        }
        cleanup();
    }

    private void cleanup() {
        running = false;
        if (batchTask != null) {
            batchTask.cancel();
            batchTask = null;
        }
        if (progressTask != null) {
            progressTask.cancel();
            progressTask = null;
        }
        bossBar.removeAll();
    }

    public static GenTask createTask(World world, int radiusBlocks) {
        int centerCX = 0;
        int centerCZ = 0;
        int radiusChunks = radiusBlocks / 16;

        List<int[]> coords = new ArrayList<>();
        for (int x = centerCX - radiusChunks; x <= centerCX + radiusChunks; x++) {
            for (int z = centerCZ - radiusChunks; z <= centerCZ + radiusChunks; z++) {
                coords.add(new int[]{x, z});
            }
        }

        return new GenTask(world, coords, radiusBlocks);
    }

    public static class GenTask {
        public final World world;
        public final List<int[]> chunkCoords;
        public final int radiusBlocks;

        public GenTask(World world, List<int[]> chunkCoords, int radiusBlocks) {
            this.world = world;
            this.chunkCoords = chunkCoords;
            this.radiusBlocks = radiusBlocks;
        }
    }

    private static class QueuedChunk {
        final World world;
        final int x, z;

        QueuedChunk(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
    }
}
