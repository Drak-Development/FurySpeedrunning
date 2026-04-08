package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.enums.PluginGameMode;
import host.plas.furyspeedrunning.world.RunnerWorldBundle;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.stream.Collectors;

public class DragonListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (GameManager.getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        RunnerWorldBundle bundle = WorldManager.findBundleContaining(event.getEntity().getWorld());
        if (bundle == null || bundle.getEnd() == null || !event.getEntity().getWorld().equals(bundle.getEnd())) {
            return;
        }

        if (GameManager.isGameCompleted()) return;

        String elapsed = GameManager.getElapsedTime();
        GameManager.setGameCompleted(true);

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();

        if (GameManager.getActiveMatchMode() == PluginGameMode.VERSUS) {
            int winnerIndex = bundle.getRunnerIndex();
            List<Player> winners = PlayerManager.getOnlinePlayers().stream()
                    .filter(d -> d.getRole() == PlayerRole.PLAYER
                            && d.getVersusRunnerIndex() != null
                            && d.getVersusRunnerIndex() == winnerIndex)
                    .map(PlayerData::getPlayer)
                    .filter(p -> p != null)
                    .collect(Collectors.toList());
            String winnerNames = winners.stream().map(Player::getName).collect(Collectors.joining(", "));
            if (winnerNames.isEmpty()) {
                winnerNames = "Runner " + (winnerIndex + 1);
            }

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A7a\u00A7l\u2726 VERSUS WINNER! \u2726");
            Bukkit.broadcastMessage("\u00A77First dragon down: \u00A7e\u00A7l" + winnerNames);
            Bukkit.broadcastMessage("\u00A77Time: \u00A7e\u00A7l" + elapsed);
            Bukkit.broadcastMessage("");

            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player p = data.getPlayer();
                if (p == null) continue;
                if (data.getRole() == PlayerRole.PLAYER && data.getVersusRunnerIndex() != null
                        && data.getVersusRunnerIndex() == winnerIndex) {
                    p.sendTitle("\u00A7a\u00A7lVICTORY!", "\u00A77You finished first!", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.PLAYER) {
                    p.sendTitle("\u00A7c\u00A7lDEFEAT!", "\u00A77Your opponent beat the dragon first.", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.SPECTATOR) {
                    p.sendTitle("\u00A7e\u00A7lMATCH OVER", "\u00A77Winner: " + winnerNames, 10, 60, 20);
                }
            }

            plugin.logInfo("&a&lVersus won by runner " + winnerIndex + " in " + elapsed);
        } else {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("\u00A7a\u00A7l\u2726 SPEEDRUN COMPLETE! \u2726");
            Bukkit.broadcastMessage("\u00A77The Ender Dragon has been defeated!");
            Bukkit.broadcastMessage("\u00A77Time: \u00A7e\u00A7l" + elapsed);
            Bukkit.broadcastMessage("");

            List<Player> runners = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
            for (Player runner : runners) {
                runner.sendTitle("\u00A7a\u00A7lVICTORY!", "\u00A77You defeated the dragon!", 10, 60, 20);
            }
            for (Player imposter : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER)) {
                imposter.sendTitle("\u00A7c\u00A7lDEFEAT!", "\u00A77The speedrunners completed the run!", 10, 60, 20);
            }

            plugin.logInfo("&a&lSpeedrun completed in " + elapsed + "!");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            GameManager.stopGame();
        }, 20L * plugin.getMainConfig().getPostWinDelay());
    }
}
