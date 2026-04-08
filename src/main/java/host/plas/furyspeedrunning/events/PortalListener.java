package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.RunnerWorldBundle;
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
            event.setCancelled(true);
            return;
        }

        World from = event.getFrom().getWorld();
        if (!WorldManager.isGameWorld(from)) return;

        RunnerWorldBundle bundle = WorldManager.findBundleContaining(from);
        if (bundle == null) return;

        World overworld = bundle.getOverworld();
        World nether = bundle.getNether();
        World end = bundle.getEnd();

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location to = event.getTo();
        if (to == null) return;

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            Location fromLoc = event.getFrom();
            if (from.equals(overworld) && nether != null) {
                Location netherDest = new Location(
                        nether,
                        fromLoc.getX() / 8.0,
                        fromLoc.getY(),
                        fromLoc.getZ() / 8.0,
                        fromLoc.getYaw(),
                        fromLoc.getPitch()
                );
                event.setTo(netherDest);
            } else if (from.equals(nether) && overworld != null) {
                Location overworldDest = new Location(
                        overworld,
                        fromLoc.getX() * 8.0,
                        fromLoc.getY(),
                        fromLoc.getZ() * 8.0,
                        fromLoc.getYaw(),
                        fromLoc.getPitch()
                );
                event.setTo(overworldDest);
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (from.equals(overworld) && end != null) {
                Location endSpawn = new Location(end, 100.5, 49, 0.5);
                event.setTo(endSpawn);
            } else if (from.equals(end)) {
                if (GameManager.isGameCompleted()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("\u00A77The game will end shortly. Please wait.");
                } else if (overworld != null) {
                    Location overworldSpawn = overworld.getSpawnLocation();
                    event.setTo(overworldSpawn);
                }
            }
        }
    }
}
