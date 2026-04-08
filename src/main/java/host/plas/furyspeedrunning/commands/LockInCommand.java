package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LockInCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("furyspeedrunning.manage")) {
            sender.sendMessage("\u00A7cYou don't have permission to use this command.");
            return true;
        }

        if (GameManager.getState() != GameState.PLAYING) {
            sender.sendMessage("\u00A7cThere is no game in progress!");
            return true;
        }

        // Get the imposter
        List<Player> hunters = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER);
        if (hunters.isEmpty()) {
            sender.sendMessage("\u00A7cNo imposter found in the current game!");
            return true;
        }
        UUID imposterUuid = hunters.get(0).getUniqueId();
        String imposterName = hunters.get(0).getName();

        // Get all non-imposter active players (voters)
        List<Player> voters = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
        if (voters.isEmpty()) {
            sender.sendMessage("\u00A7cNo players to evaluate votes from!");
            return true;
        }

        // Check if all voters have voted
        Map<UUID, UUID> votes = GameManager.getVotes();
        int totalVoters = voters.size();
        int votedCount = 0;
        int correctVotes = 0;

        for (Player voter : voters) {
            UUID vote = votes.get(voter.getUniqueId());
            if (vote != null) {
                votedCount++;
                if (vote.equals(imposterUuid)) {
                    correctVotes++;
                }
            }
        }

        // Report vote status
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("\u00A76\u00A7l--- VOTE RESULTS ---");
        Bukkit.broadcastMessage("\u00A77Votes cast: \u00A7e" + votedCount + "/" + totalVoters);

        boolean timerExpired = GameManager.getRemainingMs() <= 0;

        if (correctVotes == totalVoters && totalVoters > 0) {
            // All non-imposters unanimously voted for the imposter - imposter loses
            Bukkit.broadcastMessage("\u00A7a\u00A7lUNANIMOUS! \u00A77All players correctly identified the Imposter!");
            Bukkit.broadcastMessage("\u00A7c\u00A7l" + imposterName + " \u00A77was the Imposter!");
            Bukkit.broadcastMessage("");

            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player p = data.getPlayer();
                if (p == null) continue;
                if (data.getRole() == PlayerRole.HUNTER) {
                    p.sendTitle("\u00A7c\u00A7lDEFEAT!", "\u00A77You were caught!", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.PLAYER) {
                    p.sendTitle("\u00A7a\u00A7lVICTORY!", "\u00A77The Imposter was found!", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.SPECTATOR) {
                    p.sendTitle("\u00A7e\u00A7lIMPOSTER CAUGHT!", "\u00A77" + imposterName + " was the Imposter!", 10, 60, 20);
                }
            }

            // Stop game after delay
            Bukkit.getScheduler().runTaskLater(
                    host.plas.furyspeedrunning.FurySpeedrunning.getInstance(),
                    GameManager::stopGame,
                    20L * host.plas.furyspeedrunning.FurySpeedrunning.getInstance().getMainConfig().getPostWinDelay()
            );
        } else if (timerExpired) {
            // Timer has expired and vote was not unanimous - imposter wins
            Bukkit.broadcastMessage("\u00A7c\u00A7lThe vote was not unanimous! \u00A77The Imposter wins!");
            Bukkit.broadcastMessage("\u00A7c\u00A7l" + imposterName + " \u00A77was the Imposter!");
            Bukkit.broadcastMessage("");

            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                Player p = data.getPlayer();
                if (p == null) continue;
                if (data.getRole() == PlayerRole.HUNTER) {
                    p.sendTitle("\u00A7a\u00A7lVICTORY!", "\u00A77You got away with it!", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.PLAYER) {
                    p.sendTitle("\u00A7c\u00A7lDEFEAT!", "\u00A77The Imposter escaped!", 10, 60, 20);
                } else if (data.getRole() == PlayerRole.SPECTATOR) {
                    p.sendTitle("\u00A7e\u00A7lIMPOSTER WINS!", "\u00A77" + imposterName + " was the Imposter!", 10, 60, 20);
                }
            }

            // Stop game after delay
            Bukkit.getScheduler().runTaskLater(
                    host.plas.furyspeedrunning.FurySpeedrunning.getInstance(),
                    GameManager::stopGame,
                    20L * host.plas.furyspeedrunning.FurySpeedrunning.getInstance().getMainConfig().getPostWinDelay()
            );
        } else {
            // Timer hasn't expired yet - can't determine imposter win, just show status
            Bukkit.broadcastMessage("\u00A77The vote was not unanimous.");
            Bukkit.broadcastMessage("\u00A77The timer has not expired yet \u2014 the game continues!");
            Bukkit.broadcastMessage("");
            // Clear votes so they can vote again
            GameManager.clearVotes();
            sender.sendMessage("\u00A7eVotes have been cleared. Players can vote again.");
        }

        return true;
    }
}
