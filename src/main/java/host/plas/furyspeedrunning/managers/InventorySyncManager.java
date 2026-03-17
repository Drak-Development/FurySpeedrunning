package host.plas.furyspeedrunning.managers;

import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public class InventorySyncManager {
    private static volatile boolean syncing = false;

    public static boolean isSyncing() {
        return syncing;
    }

    public static void syncInventory(Player source) {
        if (syncing) return;
        syncing = true;

        try {
            List<Player> players = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
            PlayerInventory sourceInv = source.getInventory();

            for (Player target : players) {
                if (target.equals(source)) continue;

                PlayerInventory targetInv = target.getInventory();

                // Sync main inventory (slots 0-35)
                for (int i = 0; i < 36; i++) {
                    ItemStack item = sourceInv.getItem(i);
                    targetInv.setItem(i, item != null ? item.clone() : null);
                }

                // Sync armor
                ItemStack[] armor = sourceInv.getArmorContents();
                ItemStack[] clonedArmor = new ItemStack[armor.length];
                for (int i = 0; i < armor.length; i++) {
                    clonedArmor[i] = armor[i] != null ? armor[i].clone() : null;
                }
                targetInv.setArmorContents(clonedArmor);

                // Sync offhand
                ItemStack offhand = sourceInv.getItemInOffHand();
                targetInv.setItemInOffHand(offhand.getType().isAir() ? null : offhand.clone());
            }
        } finally {
            syncing = false;
        }
    }

    public static void syncHealth(Player source) {
        if (syncing) return;
        syncing = true;

        try {
            List<Player> players = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
            double health = source.getHealth();
            int foodLevel = source.getFoodLevel();
            float saturation = source.getSaturation();

            for (Player target : players) {
                if (target.equals(source)) continue;
                target.setHealth(Math.max(0, Math.min(health, target.getMaxHealth())));
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
            List<Player> players = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
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

    public static void reset() {
        syncing = false;
    }
}
