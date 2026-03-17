package host.plas.furyspeedrunning.data;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.world.LobbyManager;
import host.plas.furyspeedrunning.world.WorldManager;
import host.plas.furyspeedrunning.managers.InventorySyncManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

public class GameManager {
    @Getter @Setter
    private static GameState state = GameState.LOBBY;

    @Getter @Setter
    private static long gameStartTime = 0;

    @Getter @Setter
    private static boolean gameCompleted = false;

    private static final Random RANDOM = new Random();

    public static void startGame() {
        if (state == GameState.PLAYING) return;

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        List<Long> seeds = plugin.getMainConfig().getSeeds();
        long seed = seeds.get(RANDOM.nextInt(seeds.size()));

        plugin.logInfo("&aStarting speedrun with seed: &e" + seed);
        Bukkit.broadcastMessage("§e§lPreparing world... §7Please wait.");

        // Create worlds
        WorldManager.createGameWorlds(seed);
        World overworld = WorldManager.getOverworld();
        if (overworld == null) {
            plugin.logSevere("Failed to create game worlds!");
            Bukkit.broadcastMessage("§c§lFailed to create game worlds!");
            return;
        }

        // Pre-generate chunks, then start
        int radius = plugin.getMainConfig().getPreGenerateRadius();
        plugin.logInfo("&aPre-generating chunks in radius " + radius + "...");

        WorldManager.preGenerateChunks(overworld, radius, () -> {
            state = GameState.PLAYING;
            gameStartTime = System.currentTimeMillis();
            gameCompleted = false;

            Location spawn = overworld.getSpawnLocation().add(0.5, 1, 0.5);

            // Teleport and set up players
            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player player = data.getPlayer();
                if (player == null) continue;

                player.getInventory().clear();
                player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

                if (data.getRole() == PlayerRole.PLAYER) {
                    setupPlayerRole(player);
                } else {
                    setupSpectatorRole(player);
                }

                player.teleport(spawn);
            }

            // Hide spectators from players
            applySpectatorVisibility();

            plugin.logInfo("&a&lSpeedrun started! Seed: &e" + seed);
            Bukkit.broadcastMessage("§a§lSpeedrun started! §7Good luck!");
        });
    }

    public static void stopGame() {
        if (state == GameState.LOBBY) return;

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        state = GameState.LOBBY;
        gameStartTime = 0;
        gameCompleted = false;

        // Reset syncing state
        InventorySyncManager.reset();

        // Teleport all players to lobby
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            Player player = data.getPlayer();
            if (player == null) continue;

            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.getInventory().clear();
            player.setExp(0);
            player.setLevel(0);
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

            LobbyManager.sendToLobby(player);
            LobbyManager.giveLobbyItems(player);

            // Show all players to each other again
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            }
        }

        // Delete game worlds
        WorldManager.deleteGameWorlds();

        plugin.logInfo("&c&lSpeedrun stopped and cleaned up.");
        Bukkit.broadcastMessage("§c§lSpeedrun ended! §7Returning to lobby.");
    }

    public static void setupPlayerRole(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public static void setupSpectatorRole(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        // Give spectator items
        LobbyManager.giveSpectatorItems(player);
    }

    public static void applySpectatorVisibility() {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        List<Player> spectators = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.SPECTATOR);
        List<Player> players = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);

        // Hide spectators from players
        for (Player spectator : spectators) {
            for (Player gamePlayer : players) {
                gamePlayer.hidePlayer(plugin, spectator);
            }
        }

        // Spectators can see everyone
        for (Player spectator : spectators) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                spectator.showPlayer(plugin, other);
            }
        }
    }

    public static String getElapsedTime() {
        if (gameStartTime == 0) return "00:00";
        long elapsed = System.currentTimeMillis() - gameStartTime;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / 1000 / 60) % 60;
        long hours = elapsed / 1000 / 60 / 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
