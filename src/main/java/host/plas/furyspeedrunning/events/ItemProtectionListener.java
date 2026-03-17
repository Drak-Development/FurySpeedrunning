package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.gui.LobbyGui;
import host.plas.furyspeedrunning.gui.SpectatorGui;
import host.plas.furyspeedrunning.world.LobbyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class ItemProtectionListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // In lobby, prevent all inventory interaction
        if (GameManager.getState() == GameState.LOBBY) {
            event.setCancelled(true);
            return;
        }

        // During game, only protect GUI items
        ItemStack clicked = event.getCurrentItem();
        if (LobbyManager.isGuiItem(clicked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (GameManager.getState() == GameState.LOBBY) {
            event.setCancelled(true);
            return;
        }

        if (LobbyManager.isGuiItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (GameManager.getState() == GameState.LOBBY) {
            event.setCancelled(true);
            return;
        }

        if (LobbyManager.isGuiItem(event.getOffHandItem()) || LobbyManager.isGuiItem(event.getMainHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!LobbyManager.isGuiItem(item)) return;

        event.setCancelled(true);

        PlayerData data = PlayerManager.getPlayer(player);
        if (data == null) return;

        if (GameManager.getState() == GameState.LOBBY) {
            new LobbyGui(player).open();
        } else if (GameManager.getState() == GameState.PLAYING && data.getRole() == PlayerRole.SPECTATOR) {
            new SpectatorGui(player).open();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLobbyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (GameManager.getState() == GameState.LOBBY) {
            event.setCancelled(true);
        }
    }
}
