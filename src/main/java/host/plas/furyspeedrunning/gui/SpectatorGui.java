package host.plas.furyspeedrunning.gui;

import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.PlayerRole;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class SpectatorGui extends Gui {

    public SpectatorGui(Player player) {
        super(player, "spectator-teleport", "§b§lSpectate Players", 3);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new Icon(Material.GRAY_STAINED_GLASS_PANE).setName(" "));

        List<Player> gamePlayers = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);

        if (gamePlayers.isEmpty()) {
            addItem(13, new Icon(Material.BARRIER)
                    .setName("§cNo players in game")
                    .setLore(" ", "§7There are no active players to spectate."));
            return;
        }

        // Place player heads starting from slot 10, skipping edges
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int slotIndex = 0;

        for (Player gamePlayer : gamePlayers) {
            if (slotIndex >= slots.length) break;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(gamePlayer);
                meta.setDisplayName("§e§l" + gamePlayer.getName());
                skull.setItemMeta(meta);
            }

            Icon icon = new Icon(skull)
                    .setLore(
                            " ",
                            "§7World: §f" + gamePlayer.getWorld().getName(),
                            "§7Health: §c" + String.format("%.1f", gamePlayer.getHealth()) + " §7/ §c" + String.format("%.1f", gamePlayer.getMaxHealth()),
                            " ",
                            "§eClick to teleport"
                    )
                    .onClick(e -> {
                        player.teleport(gamePlayer.getLocation());
                        player.closeInventory();
                        player.sendMessage("§bTeleported to §e" + gamePlayer.getName() + "§b!");
                    });

            addItem(slots[slotIndex], icon);
            slotIndex++;
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        return false;
    }
}
