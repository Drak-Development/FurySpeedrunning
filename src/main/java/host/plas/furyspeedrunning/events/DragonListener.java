package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

public class DragonListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (GameManager.getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // Only trigger for our game's end world
        if (!event.getEntity().getWorld().equals(WorldManager.getEnd())) return;

        String elapsed = GameManager.getElapsedTime();

        Bukkit.broadcastMessage("§a§l✦ SPEEDRUN COMPLETE! ✦");
        Bukkit.broadcastMessage("§7The Ender Dragon has been defeated!");
        Bukkit.broadcastMessage("§7Time: §e§l" + elapsed);

        FurySpeedrunning plugin = FurySpeedrunning.getInstance();
        plugin.logInfo("&a&lSpeedrun completed in " + elapsed + "!");

        // Return to lobby after delay
        int delay = plugin.getMainConfig().getPostWinDelay();
        Bukkit.broadcastMessage("§7Returning to lobby in §e" + delay + "§7 seconds...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            GameManager.stopGame();
        }, 20L * delay);
    }
}
