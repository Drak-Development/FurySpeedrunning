package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ManageGameCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("furyspeedrunning.manage")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /managegame <start|stop>");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "start":
                if (GameManager.getState() == GameState.PLAYING) {
                    sender.sendMessage("§cA game is already in progress!");
                    return true;
                }
                sender.sendMessage("§aStarting speedrun...");
                GameManager.startGame();
                break;

            case "stop":
                if (GameManager.getState() == GameState.LOBBY) {
                    sender.sendMessage("§cNo game is currently running!");
                    return true;
                }
                sender.sendMessage("§cStopping speedrun...");
                GameManager.stopGame();
                break;

            default:
                sender.sendMessage("§cUsage: /managegame <start|stop>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
