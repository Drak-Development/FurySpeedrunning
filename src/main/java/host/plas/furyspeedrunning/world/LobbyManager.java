package host.plas.furyspeedrunning.world;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.MainConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class LobbyManager {
    private static final String LOBBY_WORLD_NAME = "fury_lobby";

    @Getter
    private static World lobbyWorld;

    private static NamespacedKey guiItemKey;

    private static NamespacedKey getGuiItemKey() {
        if (guiItemKey == null) {
            guiItemKey = new NamespacedKey(FurySpeedrunning.getInstance(), "gui_item");
        }
        return guiItemKey;
    }

    public static void createLobbyWorld() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        lobbyWorld = Bukkit.getWorld(LOBBY_WORLD_NAME);
        if (lobbyWorld != null) {
            plugin.logInfo("&aLobby world already loaded.");
            buildSpawnPlatform();
            return;
        }

        WorldCreator creator = new WorldCreator(LOBBY_WORLD_NAME);
        creator.generator(new VoidChunkGenerator());
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);

        lobbyWorld = creator.createWorld();
        if (lobbyWorld != null) {
            setBooleanRule(lobbyWorld, "doDaylightCycle", false);
            setBooleanRule(lobbyWorld, "doWeatherCycle", false);
            setBooleanRule(lobbyWorld, "doMobSpawning", false);
            setBooleanRule(lobbyWorld, "doFireTick", false);
            setBooleanRule(lobbyWorld, "fallDamage", false);
            setBooleanRule(lobbyWorld, "doEntityDrops", false);
            setBooleanRule(lobbyWorld, "announceAdvancements", false);
            setBooleanRule(lobbyWorld, "keepInventory", true);
            lobbyWorld.setTime(6000);
            lobbyWorld.setDifficulty(Difficulty.PEACEFUL);

            buildSpawnPlatform();
            plugin.logInfo("&aLobby world created successfully.");
        } else {
            plugin.logSevere("Failed to create lobby world!");
        }
    }

    @SuppressWarnings({"unchecked", "removal"})
    private static void setBooleanRule(World world, String ruleName, boolean value) {
        GameRule<?> rule = GameRule.getByName(ruleName);
        if (rule != null) {
            world.setGameRule((GameRule<Boolean>) rule, value);
        }
    }

    private static void buildSpawnPlatform() {
        if (lobbyWorld == null) return;

        MainConfig config = FurySpeedrunning.getInstance().getMainConfig();
        int centerX = (int) config.getLobbySpawnX();
        int y = (int) config.getLobbySpawnY() - 1;
        int centerZ = (int) config.getLobbySpawnZ();

        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                lobbyWorld.getBlockAt(x, y, z).setType(Material.SMOOTH_STONE);
            }
        }

        lobbyWorld.setSpawnLocation(centerX, y + 1, centerZ);
    }

    public static Location getLobbySpawn() {
        MainConfig config = FurySpeedrunning.getInstance().getMainConfig();
        String worldName = config.getLobbySpawnWorld();
        World world = (worldName != null && !worldName.isEmpty()) ? Bukkit.getWorld(worldName) : lobbyWorld;
        if (world == null) world = lobbyWorld;

        return new Location(
                world,
                config.getLobbySpawnX(),
                config.getLobbySpawnY(),
                config.getLobbySpawnZ(),
                (float) config.getLobbySpawnYaw(),
                (float) config.getLobbySpawnPitch()
        );
    }

    public static void sendToLobby(Player player) {
        Location spawn = getLobbySpawn();
        if (spawn == null || spawn.getWorld() == null) return;
        player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
    }

    public static void giveLobbyItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(4, createGuiItem(
                "\u00A7a\u00A7lGame Menu \u00A77(Right-Click)",
                "\u00A77Click to select your role",
                "\u00A77or start a speedrun!"
        ));
    }

    public static void giveSpectatorItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(4, createGuiItem(
                "\u00A7b\u00A7lSpectate Players \u00A77(Right-Click)",
                "\u00A77Click to teleport to",
                "\u00A77active players."
        ));
    }

    private static ItemStack createGuiItem(String name, String... lore) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.getPersistentDataContainer().set(getGuiItemKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(getGuiItemKey(), PersistentDataType.BYTE);
    }

    public static void deleteLobbyWorld() {
        if (lobbyWorld != null) {
            Bukkit.unloadWorld(lobbyWorld, false);
            lobbyWorld = null;
        }
        WorldManager.deleteWorldFolder(LOBBY_WORLD_NAME);
    }
}
