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
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitTask;

public class SharedInventoryListener extends AbstractConglomerate {
    private BukkitTask syncTask;

    public SharedInventoryListener() {
        super();
        startPeriodicSync();
    }

    /**
     * Periodic sync — only pushes the master inventory to all players.
     * Does NOT read from any player, so it can't overwrite recent changes.
     * Runs every 40 ticks (2 seconds) as a safety net for missed events.
     */
    private void startPeriodicSync() {
        syncTask = Bukkit.getScheduler().runTaskTimer(FurySpeedrunning.getInstance(), () -> {
            if (GameManager.getState() != GameState.PLAYING) return;
            InventorySyncManager.pushMasterToAll();
        }, 20L, 40L);
    }

    private boolean isActivePlayer(Player player) {
        if (GameManager.getState() != GameState.PLAYING) return false;
        PlayerData data = PlayerManager.getPlayer(player);
        return data != null && (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER);
    }

    private void scheduleSyncNextTick(Player source) {
        if (InventorySyncManager.isSyncing()) return;
        InventorySyncManager.markSyncPending();
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (source.isOnline() && isActivePlayer(source)) {
                InventorySyncManager.syncFromPlayer(source);
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
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        // Feeding, taming, trading — delayed extra tick for the item to be consumed
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (event.getPlayer().isOnline() && isActivePlayer(event.getPlayer())) {
                InventorySyncManager.syncFromPlayer(event.getPlayer());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!isActivePlayer(event.getEnchanter())) return;
        scheduleSyncNextTick(event.getEnchanter());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!isActivePlayer(event.getPlayer())) return;
        scheduleSyncNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        Player player = (Player) event.getEntered();
        if (!isActivePlayer(player)) return;
        // Placing a boat/minecart consumes the item — sync after a short delay
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player)) {
                InventorySyncManager.syncFromPlayer(player);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        Player player = (Player) event.getExited();
        if (!isActivePlayer(player)) return;
        scheduleSyncNextTick(player);
    }
}
