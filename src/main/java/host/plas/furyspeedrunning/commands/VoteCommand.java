package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.enums.PluginGameMode;
import host.plas.furyspeedrunning.gui.VoteGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VoteCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (GameManager.getState() != GameState.PLAYING) {
            player.sendMessage("\u00A7cThere is no game in progress!");
            return true;
        }

        if (GameManager.getActiveMatchMode() == PluginGameMode.VERSUS) {
            player.sendMessage("\u00A7cVoting is only used in coop (imposter) mode.");
            return true;
        }

        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null || (data.getRole() != PlayerRole.PLAYER && data.getRole() != PlayerRole.HUNTER)) {
            player.sendMessage("\u00A7cOnly active players can vote!");
            return true;
        }

        if (args.length >= 1) {
            // Direct vote by player name
            String targetName = args[0];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("\u00A7cPlayer not found: " + targetName);
                return true;
            }

            PlayerData targetData = PlayerManager.getPlayer(target);
            if (targetData == null || (targetData.getRole() != PlayerRole.PLAYER && targetData.getRole() != PlayerRole.HUNTER)) {
                player.sendMessage("\u00A7cThat player is not an active participant!");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage("\u00A7cYou cannot vote for yourself!");
                return true;
            }

            GameManager.setVote(player.getUniqueId(), target.getUniqueId());
            player.sendMessage("\u00A7aYou voted for \u00A7e" + target.getName() + " \u00A7aas the Imposter!");
        } else {
            // Open vote GUI
            new VoteGui(player).open();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            List<String> names = new ArrayList<>();
            for (Player p : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER)) {
                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    names.add(p.getName());
                }
            }
            for (Player p : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER)) {
                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    names.add(p.getName());
                }
            }
            return names.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
