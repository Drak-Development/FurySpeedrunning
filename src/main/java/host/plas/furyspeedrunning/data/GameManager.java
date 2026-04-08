package host.plas.furyspeedrunning.data;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.enums.PluginGameMode;
import host.plas.furyspeedrunning.events.HunterListener;
import host.plas.furyspeedrunning.managers.InventorySyncManager;
import host.plas.furyspeedrunning.managers.LootModifier;
import host.plas.furyspeedrunning.config.MainConfig;
import host.plas.furyspeedrunning.config.SeedPair;
import host.plas.furyspeedrunning.world.ChunkPreGenerator;
import host.plas.furyspeedrunning.world.LobbyManager;
import host.plas.furyspeedrunning.world.RunnerWorldBundle;
import host.plas.furyspeedrunning.world.WorldManager;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    @Getter @Setter
    private static GameState state = GameState.LOBBY;

    @Getter @Setter
    private static long gameStartTime = 0;

    @Getter @Setter
    private static boolean gameCompleted = false;

    private static final Random RANDOM = new Random();

    @Getter
    private static ChunkPreGenerator preGenerator;

    // Timer system
    private static final long TIMER_DURATION_MS = 30 * 60 * 1000; // 30 minutes
    @Getter
    private static long timerElapsedMs = 0;
    @Getter
    private static boolean timerRunning = false;
    private static long timerLastResumed = 0;
    private static BukkitTask timerTask;

    // Vote system
    private static final Map<UUID, UUID> votes = new ConcurrentHashMap<>();
    @Getter
    private static boolean timerExpiredNotified = false;

    @Getter
    private static PluginGameMode activeMatchMode = PluginGameMode.COOP;

    private static PluginGameMode pendingGameMode = PluginGameMode.COOP;

    /** Imposter, 30m countdown expiry, and /vote apply only in coop matches. */
    public static boolean isCoopImposterFeaturesEnabled() {
        return activeMatchMode == PluginGameMode.COOP;
    }

    public static void startGame() {
        if (state == GameState.PLAYING) return;
        if (preGenerator != null && preGenerator.isRunning()) {
            Bukkit.broadcastMessage("\u00A7cWorld is still generating. Please wait.");
            return;
        }

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        MainConfig config = plugin.getMainConfig();

        // Collect non-spectator players for role assignment
        List<PlayerData> participants = new ArrayList<>();
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            if (data.getRole() != PlayerRole.SPECTATOR) {
                participants.add(data);
            }
        }

        if (participants.size() < 2) {
            Bukkit.broadcastMessage("\u00A7cNeed at least 2 non-spectator players to start!");
            return;
        }

        List<SeedPair> allPairs = config.getSeedPairs();
        List<Integer> playedIndices = config.getPlayedSeedIndices();
        List<Integer> availableIndices = new ArrayList<>();
        for (int i = 0; i < allPairs.size(); i++) {
            if (!playedIndices.contains(i)) availableIndices.add(i);
        }
        if (availableIndices.isEmpty()) {
            Bukkit.broadcastMessage("\u00A7cAll seeds have been played! Ask an admin to add more or clear played-seeds.");
            return;
        }

        if (config.isVersusMode()) {
            if (participants.size() != 2) {
                Bukkit.broadcastMessage("\u00A7cVersus mode requires exactly 2 runners (non-spectators). Spectate with the lobby GUI.");
                return;
            }
            if (availableIndices.size() < 2) {
                Bukkit.broadcastMessage("\u00A7cVersus needs at least 2 unused seed pairs. Add seeds or reset played-seeds in config.");
                return;
            }
            Collections.shuffle(availableIndices, RANDOM);
            int idx0 = availableIndices.get(0);
            int idx1 = availableIndices.get(1);
            SeedPair pair0 = allPairs.get(idx0);
            SeedPair pair1 = allPairs.get(idx1);
            config.addPlayedSeedIndex(idx0);
            config.addPlayedSeedIndex(idx1);
            pendingGameMode = PluginGameMode.VERSUS;
            plugin.logInfo("&eVersus seeds: #" + idx0 + " OW=&b" + pair0.getOverworld()
                    + " &7| #" + idx1 + " OW=&b" + pair1.getOverworld());

            Bukkit.broadcastMessage("\u00A7e\u00A7lPreparing worlds... \u00A77Generating chunks for both runners.");

            WorldManager.createVersusGameWorlds(
                    pair0.getOverworld(), pair0.getNether(),
                    pair1.getOverworld(), pair1.getNether());

            if (WorldManager.getOverworldForRunner(0) == null || WorldManager.getOverworldForRunner(1) == null) {
                plugin.logSevere("Failed to create versus game worlds!");
                Bukkit.broadcastMessage("\u00A7c\u00A7lFailed to create game worlds!");
                return;
            }

            int radiusBlocks = config.getPreGenRadius();
            int netherRadius = Math.max(radiusBlocks / 8, 256);

            List<ChunkPreGenerator.GenTask> genTasks = new ArrayList<>();
            for (int r = 0; r < 2; r++) {
                World ow = WorldManager.getOverworldForRunner(r);
                World nw = WorldManager.getNetherForRunner(r);
                if (ow != null) genTasks.add(ChunkPreGenerator.createTask(ow, radiusBlocks));
                if (nw != null) genTasks.add(ChunkPreGenerator.createTask(nw, netherRadius));
            }

            preGenerator = new ChunkPreGenerator();
            preGenerator.generate(genTasks, () -> {
                preGenerator = null;
                onWorldReady(participants);
            });
            return;
        }

        // --- Coop: single shared world ---
        int chosenIndex = availableIndices.get(RANDOM.nextInt(availableIndices.size()));
        SeedPair chosen = allPairs.get(chosenIndex);
        long seed = chosen.getOverworld();
        long netherSeed = chosen.getNether();
        config.addPlayedSeedIndex(chosenIndex);
        pendingGameMode = PluginGameMode.COOP;
        plugin.logInfo("&eUsing seed #" + chosenIndex + ": &b" + seed + " &7| nether seed: &b" + netherSeed);

        Bukkit.broadcastMessage("\u00A7e\u00A7lPreparing world... \u00A77Generating chunks.");

        WorldManager.createGameWorlds(seed, netherSeed);
        World overworld = WorldManager.getOverworld();
        World nether = WorldManager.getNether();
        if (overworld == null) {
            plugin.logSevere("Failed to create game worlds!");
            Bukkit.broadcastMessage("\u00A7c\u00A7lFailed to create game worlds!");
            return;
        }

        int radiusBlocks = config.getPreGenRadius();
        int netherRadius = Math.max(radiusBlocks / 8, 256);

        List<ChunkPreGenerator.GenTask> genTasks = new ArrayList<>();
        genTasks.add(ChunkPreGenerator.createTask(overworld, radiusBlocks));
        if (nether != null) {
            genTasks.add(ChunkPreGenerator.createTask(nether, netherRadius));
        }

        preGenerator = new ChunkPreGenerator();
        preGenerator.generate(genTasks, () -> {
            preGenerator = null;
            onWorldReady(participants);
        });
    }

    /**
     * Called when chunk pre-generation is complete. Sets up roles, teleports players, starts game.
     */
    private static void onWorldReady(List<PlayerData> participants) {
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        activeMatchMode = pendingGameMode;

        World overworld = WorldManager.getOverworld();
        if (overworld == null) return;

        if (activeMatchMode == PluginGameMode.VERSUS) {
            for (PlayerData p : participants) {
                p.setVersusRunnerIndex(null);
                p.setRole(PlayerRole.PLAYER);
            }
            List<PlayerData> runners = new ArrayList<>(participants);
            Collections.shuffle(runners, RANDOM);
            runners.get(0).setVersusRunnerIndex(0);
            runners.get(1).setVersusRunnerIndex(1);
            for (PlayerData d : PlayerManager.getOnlinePlayers()) {
                if (d.getRole() == PlayerRole.SPECTATOR) {
                    d.setVersusRunnerIndex(null);
                }
            }
        } else {
            for (PlayerData p : PlayerManager.getOnlinePlayers()) {
                p.setVersusRunnerIndex(null);
            }
            PlayerData imposterData = participants.get(RANDOM.nextInt(participants.size()));
            imposterData.setRole(PlayerRole.HUNTER);
            for (PlayerData p : participants) {
                if (p != imposterData) p.setRole(PlayerRole.PLAYER);
            }
        }

        state = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        gameCompleted = false;

        timerElapsedMs = 0;
        timerRunning = false;
        timerLastResumed = 0;
        startTimerDisplayTask();

        LootModifier.reset();
        InventorySyncManager.reset();

        Location coopSpawn = overworld.getSpawnLocation().add(0.5, 1, 0.5);

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

            Location dest;
            if (activeMatchMode == PluginGameMode.VERSUS) {
                if (data.getRole() == PlayerRole.SPECTATOR) {
                    dest = WorldManager.getRunnerRespawnLocation(0);
                } else {
                    dest = WorldManager.getRunnerRespawnLocation(data.getVersusRunnerIndexOrZero());
                }
                if (dest == null) dest = coopSpawn;
            } else {
                dest = coopSpawn;
            }
            player.teleport(dest);
        }

        applySpectatorVisibility();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player player = data.getPlayer();
                if (player == null) continue;
                sendRoleAnnouncement(player, data);
            }
        }, 5L);

        if (activeMatchMode == PluginGameMode.VERSUS) {
            RunnerWorldBundle b1 = WorldManager.getBundle(1);
            long s1 = b1 != null ? b1.getOverworldSeed() : 0L;
            plugin.logInfo("&a&lVersus started! Seeds (r0/r1): &e" + WorldManager.getCurrentSeed()
                    + " &7/ &e" + s1);
            Bukkit.broadcastMessage("\u00A7a\u00A7lVersus started! \u00A77First Ender Dragon kill wins. Shared health and inventory.");
        } else {
            plugin.logInfo("&a&lSpeedrun started! Seed: &e" + WorldManager.getCurrentSeed());
            Bukkit.broadcastMessage("\u00A7a\u00A7lSpeedrun started! \u00A77Beat the dragon in 30 minutes. Good luck!");
        }
    }

    public static void stopGame() {
        if (state == GameState.LOBBY) return;

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        state = GameState.LOBBY;
        gameStartTime = 0;
        gameCompleted = false;

        // Cancel any in-progress chunk generation
        if (preGenerator != null && preGenerator.isRunning()) {
            preGenerator.cancel();
            preGenerator = null;
        }

        // Reset syncing state, loot tracking, boss bar, timer, and votes
        InventorySyncManager.reset();
        LootModifier.reset();
        HunterListener.removeBossBar();
        stopTimerTask();
        clearVotes();
        timerExpiredNotified = false;

        activeMatchMode = PluginGameMode.COOP;
        for (PlayerData data : PlayerManager.getRegisteredPlayers()) {
            data.setVersusRunnerIndex(null);
        }

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

            // Reset all advancements
            resetAdvancements(player);

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
        Bukkit.broadcastMessage("\u00A7c\u00A7lSpeedrun ended! \u00A77Returning to lobby.");
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

    private static void resetAdvancements(Player player) {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement advancement = it.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
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

    private static void sendRoleAnnouncement(Player player, PlayerData data) {
        PlayerRole role = data.getRole();
        switch (role) {
            case PLAYER:
                if (activeMatchMode == PluginGameMode.VERSUS && data.getVersusRunnerIndex() != null) {
                    int lane = data.getVersusRunnerIndex() + 1;
                    player.sendMessage("\u00A7e\u00A7lVersus: \u00A77You are runner \u00A7f" + lane
                            + "\u00A77. Different seed than your opponent; shared inventory and health.");
                }
                break;
            case HUNTER:
                player.sendTitle(
                        "\u00A77You are the",
                        "\u00A7c\u00A7lImposter",
                        10, 60, 20
                );
                player.sendMessage("\u00A77\u00A7oYou are an \u00A7c\u00A7o\u00A7lImposter");
                break;
            case SPECTATOR:
                player.sendTitle(
                        "\u00A7b\u00A7lSPECTATOR",
                        "\u00A77Watch the speedrun unfold!",
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

    // --- Timer system ---

    private static long getCurrentTimerMs() {
        if (timerRunning) {
            return timerElapsedMs + (System.currentTimeMillis() - timerLastResumed);
        }
        return timerElapsedMs;
    }

    public static long getRemainingMs() {
        return Math.max(0, TIMER_DURATION_MS - getCurrentTimerMs());
    }

    public static String getRemainingFormatted() {
        long remaining = getRemainingMs();
        long seconds = (remaining / 1000) % 60;
        long minutes = (remaining / 1000 / 60) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void toggleTimer() {
        if (timerRunning) {
            pauseTimer();
        } else {
            resumeTimer();
        }
    }

    public static void resumeTimer() {
        if (timerRunning) return;
        timerRunning = true;
        timerLastResumed = System.currentTimeMillis();
    }

    public static void pauseTimer() {
        if (!timerRunning) return;
        timerElapsedMs += System.currentTimeMillis() - timerLastResumed;
        timerRunning = false;
    }

    private static void startTimerDisplayTask() {
        stopTimerTask();
        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        if (activeMatchMode == PluginGameMode.VERSUS) {
            timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (state != GameState.PLAYING) return;
                String actionBarText = "\u00A7eElapsed: \u00A7f" + getElapsedTime();
                for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                    Player player = data.getPlayer();
                    if (player != null) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(actionBarText));
                    }
                }
            }, 5L, 10L);
            return;
        }

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.PLAYING) return;

            if (timerRunning && getRemainingMs() <= 0) {
                pauseTimer();
                onTimerExpired();
                return;
            }

            String remaining = getRemainingFormatted();
            String status = timerRunning ? "" : " \u00A77(PAUSED)";
            String actionBarText = "\u00A7e" + remaining + status;

            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player player = data.getPlayer();
                if (player != null) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(actionBarText));
                }
            }
        }, 5L, 10L);
    }

    private static void stopTimerTask() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        timerRunning = false;
        timerElapsedMs = 0;
    }

    private static void onTimerExpired() {
        timerExpiredNotified = true;

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("\u00A7c\u00A7l\u23F0 TIME'S UP! \u00A7r\u00A7c30 minutes have passed!");
        Bukkit.broadcastMessage("\u00A76\u00A7lAll players must now vote for the Imposter! \u00A77Use \u00A7e/vote");
        Bukkit.broadcastMessage("");

        // Send titles prompting vote
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            Player player = data.getPlayer();
            if (player == null) continue;
            if (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER) {
                player.sendTitle("\u00A7c\u00A7lTIME'S UP!", "\u00A77Vote for the Imposter with /vote", 10, 60, 20);
            } else if (data.getRole() == PlayerRole.SPECTATOR) {
                player.sendTitle("\u00A7c\u00A7lTIME'S UP!", "\u00A77Players are voting...", 10, 60, 20);
            }
        }

        // Open vote GUI for all non-imposter active players who haven't voted
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            for (Player p : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER)) {
                if (!votes.containsKey(p.getUniqueId())) {
                    new host.plas.furyspeedrunning.gui.VoteGui(p).open();
                }
            }
            // Also open for hunter (they vote too, to avoid revealing themselves)
            for (Player p : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER)) {
                if (!votes.containsKey(p.getUniqueId())) {
                    new host.plas.furyspeedrunning.gui.VoteGui(p).open();
                }
            }
        }, 40L); // 2 second delay after title
    }

    // --- Vote system ---

    public static Map<UUID, UUID> getVotes() {
        return Collections.unmodifiableMap(votes);
    }

    public static UUID getVote(UUID voter) {
        return votes.get(voter);
    }

    public static void setVote(UUID voter, UUID target) {
        votes.put(voter, target);
    }

    public static void clearVotes() {
        votes.clear();
    }
}
