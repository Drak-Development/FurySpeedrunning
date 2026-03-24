package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PlayAsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (GameManager.getState() != GameState.LOBBY) {
            player.sendMessage("§cYou can only change roles in the lobby!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /playas <player|spectator>");
            return true;
        }

        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null) {
            data = PlayerManager.getOrCreatePlayer(player);
        }

        String role = args[0].toLowerCase();

        switch (role) {
            case "player":
                data.setRole(PlayerRole.PLAYER);
                player.sendMessage("§aYou will participate in the next speedrun!");
                break;

            case "spectator":
                data.setRole(PlayerRole.SPECTATOR);
                player.sendMessage("§bYou are now set as a §lSpectator§b!");
                break;

            default:
                player.sendMessage("§cUsage: /playas <player|spectator>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("player", "spectator").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
