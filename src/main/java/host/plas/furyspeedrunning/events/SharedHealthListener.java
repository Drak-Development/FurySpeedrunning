package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.managers.InventorySyncManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;

public class SharedHealthListener extends AbstractConglomerate {

    private boolean isActivePlayer(Player player) {
        if (GameManager.getState() != GameState.PLAYING) return false;
        PlayerData data = PlayerManager.getPlayer(player);
        return data != null && (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;
        if (InventorySyncManager.isSyncing()) return;

        // Schedule health sync on next tick (after damage is applied)
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player)) {
                InventorySyncManager.syncHealth(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;
        if (InventorySyncManager.isSyncing()) return;

        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player)) {
                InventorySyncManager.syncHealth(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;
        if (InventorySyncManager.isSyncing()) return;

        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player)) {
                InventorySyncManager.syncHealth(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isActivePlayer(player)) return;

        // All participants (speedrunners + hunter) die together — shared health
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Kill all PLAYER and HUNTER role players
        for (Player teammate : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER)) {
            if (teammate.equals(player)) continue;
            if (teammate.getHealth() > 0) teammate.setHealth(0);
        }
        for (Player teammate : PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER)) {
            if (teammate.equals(player)) continue;
            if (teammate.getHealth() > 0) teammate.setHealth(0);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("\u00A7c\u00A7l\u2620 SPEEDRUN FAILED! \u2620");
        Bukkit.broadcastMessage("\u00A77All players have died!");
        Bukkit.broadcastMessage("\u00A77Time: \u00A7e\u00A7l" + GameManager.getElapsedTime());
        Bukkit.broadcastMessage("");

        // Return to lobby after delay
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            GameManager.stopGame();
        }, 20L * FurySpeedrunning.getInstance().getMainConfig().getPostWinDelay());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (GameManager.getState() != GameState.PLAYING) return;

        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null) return;

        // Respawn in the game world, not default world
        if (data.getRole() == PlayerRole.PLAYER && host.plas.furyspeedrunning.world.WorldManager.getOverworld() != null) {
            event.setRespawnLocation(
                    host.plas.furyspeedrunning.world.WorldManager.getOverworld().getSpawnLocation().add(0.5, 0, 0.5)
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!isActivePlayer(player)) return;
        if (InventorySyncManager.isSyncing()) return;

        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player)) {
                InventorySyncManager.syncExperience(player);
            }
        }, 1L);
    }
}
