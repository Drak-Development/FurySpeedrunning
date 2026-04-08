package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.File;

public class WorldManager {
    private static final RunnerWorldBundle[] EMPTY_BUNDLES = new RunnerWorldBundle[0];

    @Getter
    private static RunnerWorldBundle[] bundles = EMPTY_BUNDLES;

    @Getter
    private static String currentPrefix;

    @Getter @Setter
    private static long currentSeed;

    /**
     * Runner 0 overworld (coop world, or first runner in versus).
     */
    public static World getOverworld() {
        return getOverworldForRunner(0);
    }

    public static World getNether() {
        return getNetherForRunner(0);
    }

    public static World getEnd() {
        return getEndForRunner(0);
    }

    public static World getOverworldForRunner(int runnerIndex) {
        RunnerWorldBundle b = getBundle(runnerIndex);
        return b != null ? b.getOverworld() : null;
    }

    public static World getNetherForRunner(int runnerIndex) {
        RunnerWorldBundle b = getBundle(runnerIndex);
        return b != null ? b.getNether() : null;
    }

    public static World getEndForRunner(int runnerIndex) {
        RunnerWorldBundle b = getBundle(runnerIndex);
        return b != null ? b.getEnd() : null;
    }

    public static RunnerWorldBundle getBundle(int runnerIndex) {
        if (bundles == null || runnerIndex < 0 || runnerIndex >= bundles.length) return null;
        return bundles[runnerIndex];
    }

    public static int getBundleCount() {
        return bundles == null ? 0 : bundles.length;
    }

    public static RunnerWorldBundle findBundleContaining(World world) {
        if (world == null || bundles == null) return null;
        for (RunnerWorldBundle b : bundles) {
            if (b.containsWorld(world)) return b;
        }
        return null;
    }

    public static Location getRunnerRespawnLocation(int runnerIndex) {
        World ow = getOverworldForRunner(runnerIndex);
        if (ow == null) return null;
        return ow.getSpawnLocation().add(0.5, 1, 0.5);
    }

    /**
     * Single shared world set (coop). Worlds are named {@code prefix_r0_overworld}, etc.
     */
    public static void createGameWorlds(long seed, long netherSeed) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        currentPrefix = plugin.getMainConfig().getWorldPrefix() + "_" + System.currentTimeMillis();
        currentSeed = seed;

        RunnerWorldBundle b = createBundleInternal(0, seed, netherSeed);
        bundles = new RunnerWorldBundle[]{b};

        plugin.logInfo("&aGame worlds created: " + currentPrefix + " (coop)");
    }

    /**
     * Two independent world sets for versus. Consumes two seed pairs.
     */
    public static void createVersusGameWorlds(long seed0, long nether0, long seed1, long nether1) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        currentPrefix = plugin.getMainConfig().getWorldPrefix() + "_" + System.currentTimeMillis();
        currentSeed = seed0;

        RunnerWorldBundle b0 = createBundleInternal(0, seed0, nether0);
        RunnerWorldBundle b1 = createBundleInternal(1, seed1, nether1);
        bundles = new RunnerWorldBundle[]{b0, b1};

        plugin.logInfo("&aVersus game worlds created: " + currentPrefix
                + " &7| r0 OW: &b" + seed0 + " &7| r1 OW: &b" + seed1);
    }

    private static RunnerWorldBundle createBundleInternal(int runnerIndex, long seed, long netherSeed) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String r = "_r" + runnerIndex;
        String owName = currentPrefix + r + "_overworld";
        String nName = currentPrefix + r + "_nether";
        String eName = currentPrefix + r + "_the_end";

        World overworld = new WorldCreator(owName)
                .seed(seed).environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL).createWorld();
        World nether = new WorldCreator(nName)
                .seed(netherSeed).environment(World.Environment.NETHER)
                .type(WorldType.NORMAL).createWorld();
        World end = new WorldCreator(eName)
                .seed(seed).environment(World.Environment.THE_END)
                .type(WorldType.NORMAL).createWorld();

        configureGameWorld(overworld);
        configureGameWorld(nether);
        configureGameWorld(end);

        if (overworld != null) WorldGenModifier.modifyStructureSpacing(overworld);
        if (nether != null) WorldGenModifier.modifyStructureSpacing(nether);
        if (end != null) WorldGenModifier.modifyStructureSpacing(end);

        return new RunnerWorldBundle(runnerIndex, overworld, nether, end, seed, netherSeed);
    }

    /**
     * Creates game worlds from a pre-generated template. Chunks are already generated,
     * so no pre-generation step is needed.
     */
    public static void createGameWorldsFromTemplate(long seed) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        currentPrefix = plugin.getMainConfig().getWorldPrefix() + "_" + System.currentTimeMillis();
        currentSeed = seed;

        String r = "_r0";
        String owName = currentPrefix + r + "_overworld";
        String nName = currentPrefix + r + "_nether";
        String eName = currentPrefix + r + "_the_end";

        WorldTemplateManager.createFromTemplate(seed, owName, nName, eName);

        World overworld = new WorldCreator(owName)
                .seed(seed).environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL).createWorld();
        World nether = new WorldCreator(nName)
                .seed(seed).environment(World.Environment.NETHER)
                .type(WorldType.NORMAL).createWorld();
        World end = new WorldCreator(eName)
                .seed(seed).environment(World.Environment.THE_END)
                .type(WorldType.NORMAL).createWorld();

        configureGameWorld(overworld);
        configureGameWorld(nether);
        configureGameWorld(end);

        if (overworld != null) WorldGenModifier.modifyStructureSpacing(overworld);
        if (nether != null) WorldGenModifier.modifyStructureSpacing(nether);
        if (end != null) WorldGenModifier.modifyStructureSpacing(end);

        bundles = new RunnerWorldBundle[]{
                new RunnerWorldBundle(0, overworld, nether, end, seed, seed)
        };

        plugin.logInfo("&aGame worlds loaded from template: " + currentPrefix + " (seed: " + seed + ")");
    }

    private static void configureGameWorld(World world) {
        if (world == null) return;
        world.setDifficulty(Difficulty.NORMAL);
        setBooleanRule(world, "announceAdvancements", true);
    }

    @SuppressWarnings({"unchecked", "removal"})
    static void setBooleanRule(World world, String ruleName, boolean value) {
        GameRule<?> rule = GameRule.getByName(ruleName);
        if (rule != null) {
            world.setGameRule((GameRule<Boolean>) rule, value);
        }
    }

    /**
     * Fully cleans up game worlds — moves players out first, then unloads and deletes.
     */
    public static void deleteGameWorlds() {
        evacuatePlayersFromGameWorlds();

        if (bundles != null) {
            for (RunnerWorldBundle b : bundles) {
                unloadAndDelete(b.getOverworld());
                unloadAndDelete(b.getNether());
                unloadAndDelete(b.getEnd());
            }
        }
        bundles = EMPTY_BUNDLES;
        currentPrefix = null;
        currentSeed = 0;
    }

    private static void unloadAndDelete(World world) {
        if (world == null) return;
        String name = world.getName();
        Bukkit.unloadWorld(world, false);
        deleteWorldFolderAsync(name);
    }

    private static void evacuatePlayersFromGameWorlds() {
        World fallback = Bukkit.getWorlds().get(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isGameWorld(player.getWorld())) {
                player.teleport(fallback.getSpawnLocation());
            }
        }
    }

    public static boolean isGameWorld(World world) {
        return findBundleContaining(world) != null;
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

    /**
     * Cleans up ALL stale game worlds from previous runs.
     */
    public static void cleanupStaleWorlds() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        String prefix = plugin.getMainConfig().getWorldPrefix() + "_";
        File worldContainer = Bukkit.getWorldContainer();
        File[] files = worldContainer.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isDirectory()) continue;
            String name = file.getName();

            if (name.startsWith(prefix) || name.startsWith("template_gen_")) {
                World world = Bukkit.getWorld(name);
                if (world != null) {
                    World fallback = Bukkit.getWorlds().get(0);
                    for (Player p : world.getPlayers()) {
                        p.teleport(fallback.getSpawnLocation());
                    }
                    Bukkit.unloadWorld(world, false);
                }
                deleteRecursive(file);
                plugin.logInfo("&aCleaned up stale world: " + name);
            }
        }
    }
}
