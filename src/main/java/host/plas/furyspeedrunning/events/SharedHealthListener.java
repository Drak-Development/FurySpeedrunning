package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.managers.InventorySyncManager;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public class SharedHealthListener extends AbstractConglomerate {

    private boolean isActivePlayer(Player player) {
        if (GameManager.getState() != GameState.PLAYING) return false;
        PlayerData data = PlayerManager.getPlayer(player);
        return data != null && (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;

        double healthAfter = player.getHealth() - event.getFinalDamage();
        if (healthAfter <= 0) {
            event.setCancelled(true);

            Location deathLocation = player.getLocation();

            // Drop all items from the dying player's inventory at the death location
            PlayerInventory inv = player.getInventory();
            World deathWorld = deathLocation.getWorld();
            if (deathWorld != null) {
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        deathWorld.dropItemNaturally(deathLocation, item);
                    }
                }
            }

            // Clear master inventory and all players' inventories
            InventorySyncManager.clearMaster();

            // Respawn location: overworld world spawn
            World overworld = WorldManager.getOverworld();
            Location respawnLoc = overworld != null
                    ? overworld.getSpawnLocation().add(0.5, 1, 0.5)
                    : deathLocation;

            // Teleport all active players to overworld spawn, reset health
            List<Player> participants = getActiveParticipants();
            for (Player p : participants) {
                p.getInventory().clear();
                p.getInventory().setArmorContents(new ItemStack[4]);
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                p.teleport(respawnLoc);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setSaturation(20f);
            }
        }
    }

    private List<Player> getActiveParticipants() {
        List<Player> result = new ArrayList<>();
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            if (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER) {
                Player p = data.getPlayer();
                if (p != null) result.add(p);
            }
        }
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActivePlayer(player)) return;
        if (InventorySyncManager.isSyncing()) return;

        // Schedule health sync on next tick (after damage is applied)
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && isActivePlayer(player) && !player.isDead()) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (GameManager.getState() != GameState.PLAYING) return;
        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null) return;
        if (data.getRole() != PlayerRole.PLAYER && data.getRole() != PlayerRole.HUNTER) return;

        // This fires when damage bypassed onLethalDamage (void, /kill, etc.)
        // Handle the shared death logic here as a fallback.
        Location deathLocation = player.getLocation();

        // Drop all items from master inventory at the death location
        World deathWorld = deathLocation.getWorld();
        if (deathWorld != null) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    deathWorld.dropItemNaturally(deathLocation, item);
                }
            }
        }

        // Clear master inventory
        InventorySyncManager.clearMaster();

        // Keep inventory so vanilla doesn't also drop items (we already dropped them)
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Force instant respawn
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);

        // Clear and teleport all OTHER active players to overworld spawn
        World overworld = WorldManager.getOverworld();
        Location respawnLoc = overworld != null
                ? overworld.getSpawnLocation().add(0.5, 1, 0.5)
                : deathLocation;

        for (Player p : getActiveParticipants()) {
            if (p.equals(player)) continue; // dying player handled by respawn event
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            p.teleport(respawnLoc);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (GameManager.getState() != GameState.PLAYING) return;

        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null) return;

        if ((data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER)
                && WorldManager.getOverworld() != null) {
            event.setRespawnLocation(
                    WorldManager.getOverworld().getSpawnLocation().add(0.5, 1, 0.5)
            );

            // Heal and clear inventory after respawn
            Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
                if (!player.isOnline() || player.isDead()) return;
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                InventorySyncManager.syncHealth(player);
            }, 1L);
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
