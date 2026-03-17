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

        GameManager.setGameCompleted(true);

        Bukkit.broadcastMessage("§a§l✦ SPEEDRUN COMPLETE! ✦");
        Bukkit.broadcastMessage("§7The Ender Dragon has been defeated!");
        Bukkit.broadcastMessage("§7Time: §e§l" + elapsed);
        Bukkit.broadcastMessage("§7Use §e/managegame stop §7to return to the lobby.");

        FurySpeedrunning.getInstance().logInfo("&a&lSpeedrun completed in " + elapsed + "!");
    }
}
