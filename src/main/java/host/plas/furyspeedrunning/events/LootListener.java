package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.managers.LootModifier;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

public class LootListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (GameManager.getState() != GameState.PLAYING) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!WorldManager.isGameWorld(block.getWorld())) return;

        // Only modify containers that have a loot table (world-generated loot chests)
        if (!(block.getState() instanceof Container)) return;
        if (!(block.getState() instanceof Lootable)) return;

        Location loc = block.getLocation();
        if (LootModifier.isAlreadyModified(loc)) return;

        Lootable lootable = (Lootable) block.getState();
        LootTable table = lootable.getLootTable();
        if (table == null) return; // Not a loot chest — player-placed or already looted

        String tableKey = table.getKey().getKey(); // e.g. "chests/ruined_portal"
        LootModifier.markModified(loc);

        Player player = event.getPlayer();

        // Wait 2 ticks for vanilla loot to generate, then modify
        Bukkit.getScheduler().runTaskLater(FurySpeedrunning.getInstance(), () -> {
            if (!player.isOnline()) return;
            if (!(block.getState() instanceof Container)) return;

            Container container = (Container) block.getState();
            LootModifier.modifyLootChest(container.getInventory(), tableKey);
        }, 2L);
    }
}
