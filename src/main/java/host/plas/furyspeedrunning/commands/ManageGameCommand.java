package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.WorldTemplateManager;
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
            sender.sendMessage("\u00A7cUsage: /managegame <start|stop|status|regentemplate>");
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

            case "regentemplate":
                if (WorldTemplateManager.isGenerating()) {
                    sender.sendMessage("\u00A7cTemplate generation is already running: " + WorldTemplateManager.getGenerationStatus());
                    return true;
                }
                sender.sendMessage("\u00A7eRestarting template generation for all seeds...");
                // Delete existing templates and regenerate
                deleteAllTemplates();
                WorldTemplateManager.generateMissingTemplates(() -> {
                    sender.sendMessage("\u00A7aAll templates regenerated!");
                });
                break;

            default:
                sender.sendMessage("\u00A7cUsage: /managegame <start|stop|status|regentemplate>");
                break;
        }

        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("\u00A76\u00A7l--- FurySpeedrunning Status ---");
        sender.sendMessage("\u00A77Game state: \u00A7e" + GameManager.getState().name());

        if (GameManager.getState() == GameState.PLAYING) {
            sender.sendMessage("\u00A77Elapsed: \u00A7e" + GameManager.getElapsedTime());
            sender.sendMessage("\u00A77Completed: \u00A7e" + (GameManager.isGameCompleted() ? "Yes" : "No"));
        }

        if (WorldTemplateManager.isGenerating()) {
            sender.sendMessage("\u00A77Template gen: \u00A7e" + WorldTemplateManager.getGenerationStatus());
        } else {
            List<Long> seeds = FurySpeedrunning.getMainConfig().getSeeds();
            int ready = 0;
            for (long seed : seeds) {
                if (WorldTemplateManager.hasTemplate(seed)) ready++;
            }
            sender.sendMessage("\u00A77Templates: \u00A7e" + ready + "/" + seeds.size() + " ready");
        }
    }

    private void deleteAllTemplates() {
        java.io.File templatesDir = new java.io.File(FurySpeedrunning.getInstance().getDataFolder(), "templates");
        if (templatesDir.exists()) {
            deleteRecursive(templatesDir);
        }
    }

    private void deleteRecursive(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "status", "regentemplate").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
