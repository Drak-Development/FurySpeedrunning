package host.plas.furyspeedrunning.managers;

import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public class InventorySyncManager {
    private static volatile boolean syncing = false;
    private static volatile boolean syncPending = false;

    // Master inventory — single source of truth
    private static ItemStack[] masterContents = new ItemStack[36];
    private static ItemStack[] masterArmor = new ItemStack[4];
    private static ItemStack masterOffhand = null;

    public static boolean isSyncing() {
        return syncing;
    }

    /**
     * Returns all online PLAYER and HUNTER role players (everyone who shares inventory/health).
     */
    private static List<Player> getGameParticipants() {
        List<Player> result = new ArrayList<>();
        for (PlayerData data : PlayerManager.getOnlinePlayers()) {
            if (data.getRole() == PlayerRole.PLAYER || data.getRole() == PlayerRole.HUNTER) {
                Player p = data.getPlayer();
                if (p != null) result.add(p);
            }
        }
        return result;
    }

    private static boolean hasCursorItem(Player player) {
        return player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir();
    }

    /**
     * Mark that an event-based sync is scheduled. Prevents periodic sync
     * from pushing stale master state before the event sync runs.
     */
    public static void markSyncPending() {
        syncPending = true;
    }

    /**
     * Merge all participants' inventory changes into the master, then push to everyone.
     * Compares each player's inventory against the current master snapshot — any slot
     * that differs means that player changed it. This prevents item voiding when two
     * players modify different slots on the same tick.
     */
    public static void syncFromPlayer(Player source) {
        if (syncing) return;
        syncing = true;
        syncPending = false;

        try {
            mergeAllChanges();
            for (Player p : getGameParticipants()) {
                applyMaster(p);
            }
        } finally {
            syncing = false;
        }
    }

    /**
     * Compares every participant's inventory against the current master.
     * Any slot that differs = a player-side change that should be adopted.
     */
    private static void mergeAllChanges() {
        // Snapshot current master before comparing
        ItemStack[] oldContents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            oldContents[i] = masterContents[i] != null ? masterContents[i].clone() : null;
        }
        ItemStack[] oldArmor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            oldArmor[i] = masterArmor[i] != null ? masterArmor[i].clone() : null;
        }
        ItemStack oldOffhand = masterOffhand != null ? masterOffhand.clone() : null;

        for (Player p : getGameParticipants()) {
            if (hasCursorItem(p)) continue;
            PlayerInventory inv = p.getInventory();

            for (int i = 0; i < 36; i++) {
                ItemStack playerItem = inv.getItem(i);
                if (!itemsEqual(playerItem, oldContents[i])) {
                    masterContents[i] = playerItem != null ? playerItem.clone() : null;
                }
            }

            ItemStack[] armor = inv.getArmorContents();
            for (int i = 0; i < armor.length && i < 4; i++) {
                if (!itemsEqual(armor[i], oldArmor[i])) {
                    masterArmor[i] = armor[i] != null ? armor[i].clone() : null;
                }
            }

            ItemStack offhand = inv.getItemInOffHand();
            ItemStack playerOff = offhand.getType().isAir() ? null : offhand;
            if (!itemsEqual(playerOff, oldOffhand)) {
                masterOffhand = playerOff != null ? playerOff.clone() : null;
            }
        }
    }

    private static boolean itemsEqual(ItemStack a, ItemStack b) {
        if (a == null || (a.getType() != null && a.getType().isAir())) a = null;
        if (b == null || (b.getType() != null && b.getType().isAir())) b = null;
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    /**
     * Push master inventory to all participants. Does NOT read from any player.
     * Used as a periodic safety net to fix drift from missed events.
     */
    public static void pushMasterToAll() {
        if (syncing || syncPending) return;
        syncing = true;

        try {
            for (Player p : getGameParticipants()) {
                applyMaster(p);
            }
        } finally {
            syncing = false;
        }
    }

    private static void readIntoMaster(Player source) {
        PlayerInventory inv = source.getInventory();

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            masterContents[i] = item != null ? item.clone() : null;
        }

        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length && i < 4; i++) {
            masterArmor[i] = armor[i] != null ? armor[i].clone() : null;
        }

        ItemStack offhand = inv.getItemInOffHand();
        masterOffhand = offhand.getType().isAir() ? null : offhand.clone();
    }

    private static void pushMasterToAllExcept(Player exclude) {
        for (Player p : getGameParticipants()) {
            if (p.equals(exclude)) continue;
            applyMaster(p);
        }
    }

    private static void applyMaster(Player target) {
        if (hasCursorItem(target)) return;

        PlayerInventory inv = target.getInventory();

        for (int i = 0; i < 36; i++) {
            inv.setItem(i, masterContents[i] != null ? masterContents[i].clone() : null);
        }

        ItemStack[] clonedArmor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            clonedArmor[i] = masterArmor[i] != null ? masterArmor[i].clone() : null;
        }
        inv.setArmorContents(clonedArmor);

        inv.setItemInOffHand(masterOffhand != null ? masterOffhand.clone() : null);
    }

    public static void syncHealth(Player source) {
        if (syncing) return;
        if (source.isDead()) return;
        double health = source.getHealth();
        if (health <= 0) return;
        syncing = true;

        try {
            List<Player> players = getGameParticipants();
            int foodLevel = source.getFoodLevel();
            float saturation = source.getSaturation();

            for (Player target : players) {
                if (target.equals(source) || target.isDead()) continue;
                target.setHealth(Math.max(1, Math.min(health, target.getMaxHealth())));
                target.setFoodLevel(foodLevel);
                target.setSaturation(saturation);
            }
        } finally {
            syncing = false;
        }
    }

    public static void syncExperience(Player source) {
        if (syncing) return;
        syncing = true;

        try {
            List<Player> players = getGameParticipants();
            int level = source.getLevel();
            float exp = source.getExp();

            for (Player target : players) {
                if (target.equals(source)) continue;
                target.setLevel(level);
                target.setExp(exp);
            }
        } finally {
            syncing = false;
        }
    }

    /**
     * Clear the master inventory. Called on shared death to wipe all state.
     */
    public static void clearMaster() {
        masterContents = new ItemStack[36];
        masterArmor = new ItemStack[4];
        masterOffhand = null;
    }

    /**
     * Initialize master from a player's current inventory. Called at game start.
     */
    public static void initializeMaster(Player source) {
        readIntoMaster(source);
    }

    public static void reset() {
        syncing = false;
        syncPending = false;
        masterContents = new ItemStack[36];
        masterArmor = new ItemStack[4];
        masterOffhand = null;
    }
}
