package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PortalListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        if (GameManager.getState() != GameState.PLAYING) {
            // No portals in lobby
            event.setCancelled(true);
            return;
        }

        World from = event.getFrom().getWorld();
        if (!WorldManager.isGameWorld(from)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location to = event.getTo();
        if (to == null) return;

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            // Overworld -> Nether or Nether -> Overworld
            if (from.equals(WorldManager.getOverworld()) && WorldManager.getNether() != null) {
                to.setWorld(WorldManager.getNether());
                event.setTo(to);
            } else if (from.equals(WorldManager.getNether()) && WorldManager.getOverworld() != null) {
                to.setWorld(WorldManager.getOverworld());
                event.setTo(to);
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            // Overworld -> End
            if (from.equals(WorldManager.getOverworld()) && WorldManager.getEnd() != null) {
                Location endSpawn = WorldManager.getEnd().getSpawnLocation();
                event.setTo(endSpawn);
            } else if (from.equals(WorldManager.getEnd()) && WorldManager.getOverworld() != null) {
                // End -> Overworld (end gateway / return portal)
                Location overworldSpawn = WorldManager.getOverworld().getSpawnLocation();
                event.setTo(overworldSpawn);
            }
        }
    }
}
