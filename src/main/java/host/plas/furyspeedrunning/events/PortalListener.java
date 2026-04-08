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
            Location fromLoc = event.getFrom();
            if (from.equals(WorldManager.getOverworld()) && WorldManager.getNether() != null) {
                // Overworld -> Nether: divide coords by 8
                Location netherDest = new Location(
                        WorldManager.getNether(),
                        fromLoc.getX() / 8.0,
                        fromLoc.getY(),
                        fromLoc.getZ() / 8.0,
                        fromLoc.getYaw(),
                        fromLoc.getPitch()
                );
                event.setTo(netherDest);
            } else if (from.equals(WorldManager.getNether()) && WorldManager.getOverworld() != null) {
                // Nether -> Overworld: multiply coords by 8
                Location overworldDest = new Location(
                        WorldManager.getOverworld(),
                        fromLoc.getX() * 8.0,
                        fromLoc.getY(),
                        fromLoc.getZ() * 8.0,
                        fromLoc.getYaw(),
                        fromLoc.getPitch()
                );
                event.setTo(overworldDest);
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (from.equals(WorldManager.getOverworld()) && WorldManager.getEnd() != null) {
                // Overworld -> End: spawn on the obsidian platform
                Location endSpawn = new Location(WorldManager.getEnd(), 100.5, 49, 0.5);
                event.setTo(endSpawn);
            } else if (from.equals(WorldManager.getEnd())) {
                // End -> Overworld (return portal after dragon kill)
                // Block this — players stay until /managegame stop
                if (GameManager.isGameCompleted()) {
                    // Game auto-stops now, just let them know
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("\u00A77The game will end shortly. Please wait.");
                } else if (WorldManager.getOverworld() != null) {
                    Location overworldSpawn = WorldManager.getOverworld().getSpawnLocation();
                    event.setTo(overworldSpawn);
                }
            }
        }
    }
}
