package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

public class DragonListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (GameManager.getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // Only trigger for our game's end world
        if (!event.getEntity().getWorld().equals(WorldManager.getEnd())) return;

        String elapsed = GameManager.getElapsedTime();

        GameManager.setGameCompleted(true);

        // Speedrunners win!
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("\u00A7a\u00A7l\u2726 SPEEDRUNNERS WIN! \u2726");
        Bukkit.broadcastMessage("\u00A77The Ender Dragon has been defeated!");
        Bukkit.broadcastMessage("\u00A77Time: \u00A7e\u00A7l" + elapsed);

        List<Player> runners = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < runners.size(); i++) {
            if (i > 0) names.append(", ");
            names.append(runners.get(i).getName());
        }
        Bukkit.broadcastMessage("\u00A77Winners: \u00A7a\u00A7l" + names);
        Bukkit.broadcastMessage("");

        // Send victory titles
        for (Player runner : runners) {
            runner.sendTitle("\u00A7a\u00A7lVICTORY!", "\u00A77You defeated the dragon!", 10, 60, 20);
        }
        for (Player hunter : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER)) {
            hunter.sendTitle("\u00A7c\u00A7lDEFEAT!", "\u00A77The speedrunners escaped!", 10, 60, 20);
        }

        FurySpeedrunning.getInstance().logInfo("&a&lManhunt completed in " + elapsed + "!");

        // Auto-stop after delay
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            GameManager.stopGame();
        }, 20L * FurySpeedrunning.getInstance().getMainConfig().getPostWinDelay());
    }
}
