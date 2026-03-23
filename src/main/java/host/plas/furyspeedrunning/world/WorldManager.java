package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.File;

public class WorldManager {
    @Getter
    private static World overworld;
    @Getter
    private static World nether;
    @Getter
    private static World end;

    @Getter
    private static String currentPrefix;

    @Getter @Setter
    private static long currentSeed;

    /**
     * Creates game worlds — uses pre-generated templates if available, otherwise generates fresh.
     */
    public static void createGameWorlds(long seed) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        currentPrefix = plugin.getMainConfig().getWorldPrefix() + "_" + System.currentTimeMillis();
        currentSeed = seed;

        String owName = currentPrefix + "_overworld";
        String nName = currentPrefix + "_nether";
        String eName = currentPrefix + "_the_end";

        boolean usedTemplate = WorldTemplateManager.createFromTemplate(seed, owName, nName, eName);

        // Load worlds (from template copy or fresh generation)
        overworld = new WorldCreator(owName)
                .seed(seed).environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL).createWorld();
        nether = new WorldCreator(nName)
                .seed(seed).environment(World.Environment.NETHER)
                .type(WorldType.NORMAL).createWorld();
        end = new WorldCreator(eName)
                .seed(seed).environment(World.Environment.THE_END)
                .type(WorldType.NORMAL).createWorld();

        if (usedTemplate) {
            plugin.logInfo("&aGame worlds loaded from template for seed &b" + seed);
        } else {
            plugin.logInfo("&eNo template for seed " + seed + " \u2014 generated fresh.");
        }

        configureGameWorld(overworld);
        configureGameWorld(nether);
        configureGameWorld(end);

        // Modify structure spacing for MCSR-style closer structures
        if (overworld != null) WorldGenModifier.modifyStructureSpacing(overworld);
        if (nether != null) WorldGenModifier.modifyStructureSpacing(nether);
        if (end != null) WorldGenModifier.modifyStructureSpacing(end);

        plugin.logInfo("&aGame worlds ready: " + currentPrefix);
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
        currentSeed = 0;
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
