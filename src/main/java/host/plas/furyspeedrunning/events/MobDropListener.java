package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Material;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
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
     * Vanilla: 0-1 blaze rod.
     * Boosted: guarantee 1, chance for 2-3 total.
     */
    private void boostBlazeRodDrop(EntityDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        double dropRate = FurySpeedrunning.getInstance().getMainConfig().getMobBlazeRodDropRate();

        boolean hasRod = false;
        for (ItemStack item : drops) {
            if (item.getType() == Material.BLAZE_ROD) {
                hasRod = true;
                int extra = 1 + RANDOM.nextInt(2); // +1-2
                item.setAmount(item.getAmount() + extra);
                break;
            }
        }

        if (!hasRod && RANDOM.nextDouble() < dropRate) {
            int count = 1 + RANDOM.nextInt(2); // 1-2
            drops.add(new ItemStack(Material.BLAZE_ROD, count));
        }
    }
}
