package host.plas.furyspeedrunning.managers;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.MainConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LootModifier {
    private static final Set<Location> modifiedChests = new HashSet<>();
    private static final Random RANDOM = new Random();

    public static boolean isAlreadyModified(Location loc) {
        return modifiedChests.contains(loc);
    }

    public static void markModified(Location loc) {
        modifiedChests.add(loc);
    }

    /**
     * Modifies a loot chest's contents based on its loot table type.
     * Only called on chests that had a loot table (world-generated loot chests).
     */
    public static void modifyLootChest(Inventory inventory, String tableKey) {
        MainConfig config = FurySpeedrunning.getInstance().getMainConfig();

        // Obsidian boost — only in ruined portal chests
        if (tableKey.contains("ruined_portal")) {
            if (RANDOM.nextDouble() < config.getLootObsidianChance()) {
                int count = 2 + RANDOM.nextInt(3); // 2-4
                addToEmptySlot(inventory, new ItemStack(Material.OBSIDIAN, count));
            }
        }

        // String boost — in all loot chests
        if (RANDOM.nextDouble() < config.getLootStringChance()) {
            int count = 3 + RANDOM.nextInt(4); // 3-6
            addToEmptySlot(inventory, new ItemStack(Material.STRING, count));
        }

        // Iron minimum — if chest contains any iron, ensure at least 7 total
        if (hasIron(inventory)) {
            ensureMinimumIron(inventory, 7);
        }

        // Blacksmith chests — guarantee at least 7 iron even if none spawned
        if (isBlacksmithChest(tableKey)) {
            ensureMinimumIron(inventory, 7);
        }
    }

    private static boolean hasIron(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.IRON_INGOT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensure a chest has at least the minimum number of iron ingots.
     * If no iron exists, adds a new stack.
     */
    private static void ensureMinimumIron(Inventory inventory, int minimum) {
        int totalIron = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.IRON_INGOT) {
                totalIron += item.getAmount();
            }
        }
        if (totalIron < minimum) {
            addToEmptySlot(inventory, new ItemStack(Material.IRON_INGOT, minimum - totalIron));
        }
    }

    private static void addToEmptySlot(Inventory inventory, ItemStack item) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                emptySlots.add(i);
            }
        }
        if (!emptySlots.isEmpty()) {
            int slot = emptySlots.get(RANDOM.nextInt(emptySlots.size()));
            inventory.setItem(slot, item);
        } else {
            inventory.addItem(item);
        }
    }

    private static boolean isBlacksmithChest(String tableKey) {
        return tableKey.contains("toolsmith") || tableKey.contains("weaponsmith");
    }

    public static void reset() {
        modifiedChests.clear();
    }
}
