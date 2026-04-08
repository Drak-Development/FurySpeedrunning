package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.SeedPair;
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
            sender.sendMessage("\u00A7cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("\u00A7cUsage: /managegame <start|stop|status>");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "start":
                if (GameManager.getState() == GameState.PLAYING) {
                    sender.sendMessage("\u00A7cA game is already in progress!");
                    return true;
                }
                sender.sendMessage("\u00A7aStarting speedrun...");
                GameManager.startGame();
                break;

            case "stop":
                if (GameManager.getState() == GameState.LOBBY) {
                    sender.sendMessage("\u00A7cNo game is currently running!");
                    return true;
                }
                sender.sendMessage("\u00A7cStopping speedrun...");
                GameManager.stopGame();
                break;

            case "status":
                sendStatus(sender);
                break;

            default:
                sender.sendMessage("\u00A7cUsage: /managegame <start|stop|status>");
                break;
        }

        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("\u00A76\u00A7l--- FurySpeedrunning Status ---");
        sender.sendMessage("\u00A77Game state: \u00A7e" + GameManager.getState().name());
        sender.sendMessage("\u00A77Config mode: \u00A7e" + FurySpeedrunning.getMainConfig().getGameMode().name().toLowerCase());
        if (GameManager.getState() == GameState.PLAYING) {
            sender.sendMessage("\u00A77Active match: \u00A7e" + GameManager.getActiveMatchMode().name().toLowerCase());
            sender.sendMessage("\u00A77Elapsed: \u00A7e" + GameManager.getElapsedTime());
            sender.sendMessage("\u00A77Completed: \u00A7e" + (GameManager.isGameCompleted() ? "Yes" : "No"));
        }

        List<SeedPair> allPairs = FurySpeedrunning.getMainConfig().getSeedPairs();
        List<Integer> playedIndices = FurySpeedrunning.getMainConfig().getPlayedSeedIndices();
        int remaining = allPairs.size() - playedIndices.size();
        sender.sendMessage("\u00A77Seeds remaining: \u00A7e" + remaining + "/" + allPairs.size());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
