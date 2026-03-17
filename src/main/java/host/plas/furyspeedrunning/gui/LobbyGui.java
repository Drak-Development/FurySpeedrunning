package host.plas.furyspeedrunning.gui;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemFlag;

public class LobbyGui extends Gui {

    public LobbyGui(Player player) {
        super(player, "lobby-menu", "§6§lGame Menu", 3);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        // Fill background with glass panes
        fillGui(new Icon(Material.GRAY_STAINED_GLASS_PANE).setName(" "));

        PlayerData data = PlayerManager.getPlayer(player);
        PlayerRole currentRole = data != null ? data.getRole() : PlayerRole.PLAYER;

        // Player role button (slot 11)
        Icon playerIcon = new Icon(Material.DIAMOND_SWORD)
                .setName("§a§lPlay as Player")
                .setLore(
                        " ",
                        "§7Share inventory and health",
                        "§7with other players.",
                        " ",
                        "§7Objective: §fDefeat the Ender Dragon",
                        " ",
                        currentRole == PlayerRole.PLAYER ? "§a▶ Currently Selected" : "§eClick to select"
                )
                .hideFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .onClick(e -> {
                    if (data != null) {
                        data.setRole(PlayerRole.PLAYER);
                        player.sendMessage("§aYou are now set as a §lPlayer§a!");
                        // Refresh GUI
                        new LobbyGui(player).open();
                    }
                });

        if (currentRole == PlayerRole.PLAYER) {
            playerIcon.enchant(Enchantment.UNBREAKING, 1);
        }

        addItem(11, playerIcon);

        // Start game button (slot 13)
        Icon startIcon = new Icon(Material.EMERALD_BLOCK)
                .setName("§a§lStart Speedrun")
                .setLore(
                        " ",
                        "§7Click to begin a new",
                        "§7speedrun for all players!",
                        " ",
                        "§eRequires: §ffuryspeedrunning.manage"
                )
                .onClick(e -> {
                    if (!player.hasPermission("furyspeedrunning.manage")) {
                        player.sendMessage("§cYou don't have permission to start the game!");
                        return;
                    }
                    if (GameManager.getState() == GameState.PLAYING) {
                        player.sendMessage("§cA game is already in progress!");
                        return;
                    }
                    player.closeInventory();
                    player.sendMessage("§aStarting speedrun...");
                    GameManager.startGame();
                });

        addItem(13, startIcon);

        // Spectator role button (slot 15)
        Icon spectatorIcon = new Icon(Material.ENDER_EYE)
                .setName("§b§lPlay as Spectator")
                .setLore(
                        " ",
                        "§7Watch players in Creative mode.",
                        "§7You will be invisible to players.",
                        " ",
                        "§7Use the Nether Star to teleport",
                        "§7to active players.",
                        " ",
                        currentRole == PlayerRole.SPECTATOR ? "§a▶ Currently Selected" : "§eClick to select"
                )
                .hideFlags(ItemFlag.HIDE_ENCHANTS)
                .onClick(e -> {
                    if (data != null) {
                        data.setRole(PlayerRole.SPECTATOR);
                        player.sendMessage("§bYou are now set as a §lSpectator§b!");
                        new LobbyGui(player).open();
                    }
                });

        if (currentRole == PlayerRole.SPECTATOR) {
            spectatorIcon.enchant(Enchantment.UNBREAKING, 1);
        }

        addItem(15, spectatorIcon);
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        return false; // Cancel all clicks (icons handle their own logic)
    }
}
