package host.plas.furyspeedrunning.gui;

import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.PlayerRole;
import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class SpectatorGui extends ScreenInstance {

    public SpectatorGui(Player player) {
        super(player, FuryGuiType.SPECTATOR, buildSheet(player), true);
    }

    private static InventorySheet buildSheet(Player player) {
        InventorySheet sheet = InventorySheet.empty(27);

        // Fill background
        Icon filler = new Icon(Material.GRAY_STAINED_GLASS_PANE).setName(" ");
        for (int i = 0; i < 27; i++) {
            sheet.setIcon(i, filler);
        }

        List<Player> gamePlayers = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
        gamePlayers.addAll(PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER));

        if (gamePlayers.isEmpty()) {
            Icon noPlayers = new Icon(Material.BARRIER)
                    .setName("\u00A7cNo players in game")
                    .setLore(" ", "\u00A77There are no active players to spectate.");
            sheet.setIcon(13, noPlayers);
            return sheet;
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int slotIndex = 0;

        for (Player gamePlayer : gamePlayers) {
            if (slotIndex >= slots.length) break;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(gamePlayer);
                meta.setDisplayName("\u00A7e\u00A7l" + gamePlayer.getName());
                skull.setItemMeta(meta);
            }

            Icon icon = new Icon(skull)
                    .setLore(
                            " ",
                            "\u00A77World: \u00A7f" + gamePlayer.getWorld().getName(),
                            "\u00A77Health: \u00A7c" + String.format("%.1f", gamePlayer.getHealth()) + " \u00A77/ \u00A7c" + String.format("%.1f", gamePlayer.getMaxHealth()),
                            " ",
                            "\u00A7eClick to teleport"
                    )
                    .onClick(e -> {
                        player.teleport(gamePlayer.getLocation());
                        player.closeInventory();
                        player.sendMessage("\u00A7bTeleported to \u00A7e" + gamePlayer.getName() + "\u00A7b!");
                    });

            sheet.setIcon(slots[slotIndex], icon);
            slotIndex++;
        }

        return sheet;
    }
}
