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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.scheduler.BukkitTask;

public class SharedInventoryListener extends AbstractConglomerate {
    private BukkitTask syncTask;

    public SharedInventoryListener() {
        super();
        startPeriodicSync();
    }

    /**
     * Periodic sync every 5 ticks as a safety net.
     * Catches anything the event-based sync misses.
     */
    private void startPeriodicSync() {
        syncTask = Bukkit.getScheduler().runTaskTimer(FurySpeedrunning.getInstance(), () -> {
            if (GameManager.getState() != GameState.PLAYING) return;
            if (InventorySyncManager.isSyncing()) return;

            // Find the first online PLAYER or HUNTER role player and sync from them
            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                if (data.getRole() != PlayerRole.PLAYER && data.getRole() != PlayerRole.HUNTER) continue;
                Player player = data.getPlayer();
                if (player != null && player.isOnline()) {
                    InventorySyncManager.syncInventory(player);
                    break;
                }
            }
        }, 20L, 5L); // Start after 1s, run every 5 ticks (250ms)
    }

    private boolean isActivePlayer(Player player) {
        if (GameManager.getState() != GameState.PLAYING) return false;
        PlayerData data = PlayerManager.getPlayer(player);
        return data != null && (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER);
    }

    private void scheduleSyncNextTick(Player source) {
        if (InventorySyncManager.isSyncing()) return;
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (source.isOnline() && isActivePlayer(source)) {
                InventorySyncManager.syncInventory(source);
            }
        }, 1L);
    }

    // --- Event-based sync for immediate response ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        // Covers using items like bows, ender pearls, potions, etc.
        scheduleSyncNextTick(event.getPlayer());
    }
}
