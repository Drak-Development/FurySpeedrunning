package host.plas.furyspeedrunning.data;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.events.HunterListener;
import host.plas.furyspeedrunning.managers.InventorySyncManager;
import host.plas.furyspeedrunning.managers.LootModifier;
import host.plas.furyspeedrunning.world.LobbyManager;
import host.plas.furyspeedrunning.world.WorldManager;
import host.plas.furyspeedrunning.world.WorldTemplateManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
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
        if (WorldTemplateManager.isGenerating()) {
            Bukkit.broadcastMessage("\u00A7cTemplates are still being generated. Please wait.");
            return;
        }

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        // Collect non-spectator players for role assignment
        List<PlayerData> participants = new ArrayList<>();
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            if (data.getRole() != PlayerRole.SPECTATOR) {
                participants.add(data);
            }
        }

        if (participants.size() < 2) {
            Bukkit.broadcastMessage("\u00A7cNeed at least 2 non-spectator players to start a manhunt!");
            return;
        }

        // Pick seeds and create worlds
        List<Long> seeds = plugin.getMainConfig().getSeeds();
        long seed = seeds.get(RANDOM.nextInt(seeds.size()));

        plugin.logInfo("&aStarting manhunt with seed: &e" + seed);
        Bukkit.broadcastMessage("\u00A7e\u00A7lPreparing world... \u00A77Please wait.");

        boolean hadTemplate = WorldTemplateManager.hasTemplate(seed);
        WorldManager.createGameWorlds(seed);
        World overworld = WorldManager.getOverworld();
        if (overworld == null) {
            plugin.logSevere("Failed to create game worlds!");
            Bukkit.broadcastMessage("\u00A7c\u00A7lFailed to create game worlds!");
            return;
        }

        // Assign roles: randomly pick 1 hunter from participants, rest are speedrunners
        Collections.shuffle(participants);
        PlayerData hunterData = participants.get(0);
        hunterData.setRole(PlayerRole.HUNTER);
        for (int i = 1; i < participants.size(); i++) {
            participants.get(i).setRole(PlayerRole.PLAYER);
        }

        state = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        gameCompleted = false;

        // Reset loot tracker
        LootModifier.reset();

        Location spawn = overworld.getSpawnLocation().add(0.5, 1, 0.5);

        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            Player player = data.getPlayer();
            if (player == null) continue;

            player.getInventory().clear();
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

            switch (data.getRole()) {
                case PLAYER:
                    setupPlayerRole(player);
                    break;
                case HUNTER:
                    setupHunterRole(player);
                    break;
                case SPECTATOR:
                    setupSpectatorRole(player);
                    break;
            }

            player.teleport(spawn);
        }

        applySpectatorVisibility();

        // Send role titles and chat messages (1 tick delay so players are teleported)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player player = data.getPlayer();
                if (player == null) continue;
                sendRoleAnnouncement(player, data.getRole());
            }

            // Broadcast roles to chat
            Player hunter = hunterData.getPlayer();
            String hunterName = hunter != null ? hunter.getName() : "Unknown";
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A76\u00A7l\u00A7m-----------\u00A7r \u00A7e\u00A7lMANHUNT \u00A76\u00A7l\u00A7m-----------");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A7c\u00A7lHunter: \u00A7f" + hunterName);
            Bukkit.broadcastMessage("\u00A7a\u00A7lSpeedrunners: \u00A7f" + getSpeedrunnerNames());
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A77Speedrunners must defeat the Ender Dragon.");
            Bukkit.broadcastMessage("\u00A77The Hunter must eliminate all speedrunners.");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A76\u00A7l\u00A7m-------------------------------");
            Bukkit.broadcastMessage("");
        }, 5L);

        plugin.logInfo("&a&lManhunt started! Seed: &e" + seed);
        Bukkit.broadcastMessage("\u00A7a\u00A7lManhunt started! \u00A77Good luck!");
    }

    public static void stopGame() {
        if (state == GameState.LOBBY) return;

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        state = GameState.LOBBY;
        gameStartTime = 0;
        gameCompleted = false;

        // Reset syncing state, loot tracking, and boss bar
        InventorySyncManager.reset();
        LootModifier.reset();
        HunterListener.removeBossBar();

        // Teleport all players to lobby
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            Player player = data.getPlayer();
            if (player == null) continue;

            // Reset all roles to PLAYER for lobby
            data.setRole(PlayerRole.PLAYER);

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

        plugin.logInfo("&c&lManhunt stopped and cleaned up.");
        Bukkit.broadcastMessage("\u00A7c\u00A7lManhunt ended! \u00A77Returning to lobby.");
    }

    public static void setupPlayerRole(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public static void setupHunterRole(Player player) {
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
        List<Player> hunters = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER);

        // Hide spectators from players and hunters
        for (Player spectator : spectators) {
            for (Player gamePlayer : players) {
                gamePlayer.hidePlayer(plugin, spectator);
            }
            for (Player hunter : hunters) {
                hunter.hidePlayer(plugin, spectator);
            }
        }

        // Spectators can see everyone
        for (Player spectator : spectators) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                spectator.showPlayer(plugin, other);
            }
        }
    }

    private static void sendRoleAnnouncement(Player player, PlayerRole role) {
        switch (role) {
            case PLAYER:
                player.sendTitle(
                        "\u00A7a\u00A7lSPEEDRUNNER",
                        "\u00A77Defeat the Ender Dragon to win!",
                        10, 60, 20
                );
                player.sendMessage("\u00A7a\u00A7lYou are a SPEEDRUNNER! \u00A77Kill the Ender Dragon before the hunter gets you.");
                break;
            case HUNTER:
                player.sendTitle(
                        "\u00A7c\u00A7lHUNTER",
                        "\u00A77Eliminate all speedrunners!",
                        10, 60, 20
                );
                player.sendMessage("\u00A7c\u00A7lYou are the HUNTER! \u00A77Use the boss bar to track survivors. Sabotage the speedrun!");
                break;
            case SPECTATOR:
                player.sendTitle(
                        "\u00A7b\u00A7lSPECTATOR",
                        "\u00A77Watch the manhunt unfold!",
                        10, 60, 20
                );
                player.sendMessage("\u00A7b\u00A7lYou are a SPECTATOR! \u00A77Use the Nether Star to teleport to players.");
                break;
        }
    }

    private static String getSpeedrunnerNames() {
        List<Player> runners = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
        if (runners.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runners.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(runners.get(i).getName());
        }
        return sb.toString();
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
