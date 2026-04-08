package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Item;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class MobDropListener extends AbstractConglomerate {
    private static final Random RANDOM = new Random();

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent event) {
        if (GameManager.getState() != GameState.PLAYING) return;
        if (!WorldManager.isGameWorld(event.getEntity().getWorld())) return;

        if (event.getEntity() instanceof Enderman) {
            boostEnderPearlDrop(event);
        } else if (event.getEntity() instanceof Blaze) {
            boostBlazeRodDrop(event);
        }
    }

    /**
     * When a piglin drops a barter result, chance to replace it with ender pearls.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPiglinDrop(EntityDropItemEvent event) {
        if (GameManager.getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof Piglin)) return;
        if (!WorldManager.isGameWorld(event.getEntity().getWorld())) return;

        Item droppedItem = event.getItemDrop();
        ItemStack stack = droppedItem.getItemStack();

        // If piglin already dropped pearls, boost the amount
        if (stack.getType() == Material.ENDER_PEARL) {
            int extra = 2 + RANDOM.nextInt(3); // +2-4
            stack.setAmount(stack.getAmount() + extra);
            droppedItem.setItemStack(stack);
            return;
        }

        // For non-pearl barter results, chance to replace with pearls
        double pearlRate = FurySpeedrunning.getInstance().getMainConfig().getPiglinPearlRate();
        if (RANDOM.nextDouble() < pearlRate) {
            int count = 4 + RANDOM.nextInt(4); // 4-7 pearls
            droppedItem.setItemStack(new ItemStack(Material.ENDER_PEARL, count));
        }
    }

    /**
     * Vanilla: 0-1 ender pearl.
     * Boosted: guarantee 1, chance for 2-3 total.
     */
    private void boostEnderPearlDrop(EntityDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        double dropRate = FurySpeedrunning.getInstance().getMainConfig().getMobPearlDropRate();

        // Check if any pearl already in drops
        boolean hasPearl = false;
        for (ItemStack item : drops) {
            if (item.getType() == Material.ENDER_PEARL) {
                hasPearl = true;
                // Boost existing pearl amount
                int extra = 1 + RANDOM.nextInt(2); // +1-2
                item.setAmount(item.getAmount() + extra);
                break;
            }
        }

        // If no pearl dropped, force one based on configured rate
        if (!hasPearl && RANDOM.nextDouble() < dropRate) {
            int count = 1 + RANDOM.nextInt(2); // 1-2
            drops.add(new ItemStack(Material.ENDER_PEARL, count));
        }
    }

    /**
     * 50% chance to drop exactly 1 blaze rod.
     * Replaces any vanilla rod drops with our controlled drop.
     */
    private void boostBlazeRodDrop(EntityDeathEvent event) {
        List<ItemStack> drops = event.getDrops();

        // Remove any existing blaze rod drops
        drops.removeIf(item -> item.getType() == Material.BLAZE_ROD);

        // 50% chance for exactly 1 rod
        if (RANDOM.nextDouble() < 0.50) {
            drops.add(new ItemStack(Material.BLAZE_ROD, 1));
        }
    }
}
