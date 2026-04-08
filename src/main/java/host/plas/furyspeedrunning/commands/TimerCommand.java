package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PluginGameMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TimerCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("furyspeedrun.admin.timer")) {
            sender.sendMessage("\u00A7cYou don't have permission to control the timer.");
            return true;
        }

        if (GameManager.getState() != GameState.PLAYING) {
            sender.sendMessage("\u00A7cNo game is currently running.");
            return true;
        }

        if (GameManager.getActiveMatchMode() == PluginGameMode.VERSUS) {
            sender.sendMessage("\u00A7cThe imposter timer only applies to coop mode. Versus shows elapsed time automatically.");
            return true;
        }

        GameManager.toggleTimer();

        if (GameManager.isTimerRunning()) {
            Bukkit.broadcastMessage("\u00A7e\u00A7lTimer started! \u00A77" + GameManager.getRemainingFormatted() + " remaining.");
        } else {
            Bukkit.broadcastMessage("\u00A7e\u00A7lTimer paused! \u00A77" + GameManager.getRemainingFormatted() + " remaining.");
        }

        return true;
    }
}
