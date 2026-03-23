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

    public static void reset() {
        modifiedChests.clear();
    }
}
