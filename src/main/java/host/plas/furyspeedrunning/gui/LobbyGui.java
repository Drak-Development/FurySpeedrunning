package host.plas.furyspeedrunning.gui;

import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class LobbyGui extends ScreenInstance {

    public LobbyGui(Player player) {
        super(player, FuryGuiType.LOBBY, buildSheet(player), true);
    }

    private static InventorySheet buildSheet(Player player) {
        InventorySheet sheet = InventorySheet.empty(27);

        // Fill background
        Icon filler = new Icon(Material.GRAY_STAINED_GLASS_PANE).setName(" ");
        for (int i = 0; i < 27; i++) {
            sheet.setIcon(i, filler);
        }

        PlayerData data = PlayerManager.getPlayer(player);
        PlayerRole currentRole = data != null ? data.getRole() : PlayerRole.PLAYER;

        // Join game button (slot 11)
        Icon playerIcon = new Icon(Material.DIAMOND_SWORD)
                .setName("\u00A7a\u00A7lJoin Manhunt")
                .setLore(
                        " ",
                        "\u00A77Join the next manhunt game.",
                        "\u00A77Role assigned at game start:",
                        "\u00A7a  Speedrunner \u00A77or \u00A7cHunter",
                        " ",
                        "\u00A77Speedrunners share inventory & health.",
                        "\u00A77The Hunter must eliminate them.",
                        " ",
                        currentRole == PlayerRole.PLAYER ? "\u00A7a\u25B6 Currently Selected" : "\u00A7eClick to select"
                )
                .hideFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .onClick(e -> {
                    if (data != null) {
                        data.setRole(PlayerRole.PLAYER);
                        player.sendMessage("\u00A7aYou will participate in the next manhunt!");
                        new LobbyGui(player).open();
                    }
                });

        if (currentRole == PlayerRole.PLAYER) {
            playerIcon.enchant(Enchantment.DURABILITY, 1);
        }

        sheet.setIcon(11, playerIcon);

        // Start game button (slot 13)
        Icon startIcon = new Icon(Material.EMERALD_BLOCK)
                .setName("\u00A7a\u00A7lStart Manhunt")
                .setLore(
                        " ",
                        "\u00A77Click to begin a new",
                        "\u00A77manhunt for all players!",
                        " ",
                        "\u00A77One player will become the Hunter.",
                        "\u00A77Everyone starts with nothing.",
                        " ",
                        "\u00A7eRequires: \u00A7ffuryspeedrunning.manage"
                )
                .onClick(e -> {
                    if (!player.hasPermission("furyspeedrunning.manage")) {
                        player.sendMessage("\u00A7cYou don't have permission to start the game!");
                        return;
                    }
                    if (GameManager.getState() == GameState.PLAYING) {
                        player.sendMessage("\u00A7cA game is already in progress!");
                        return;
                    }
                    player.closeInventory();
                    player.sendMessage("\u00A7aStarting manhunt...");
                    GameManager.startGame();
                });

        sheet.setIcon(13, startIcon);

        // Spectator role button (slot 15)
        Icon spectatorIcon = new Icon(Material.ENDER_EYE)
                .setName("\u00A7b\u00A7lPlay as Spectator")
                .setLore(
                        " ",
                        "\u00A77Watch players in Creative mode.",
                        "\u00A77You will be invisible to players.",
                        " ",
                        "\u00A77Use the Nether Star to teleport",
                        "\u00A77to active players.",
                        " ",
                        currentRole == PlayerRole.SPECTATOR ? "\u00A7a\u25B6 Currently Selected" : "\u00A7eClick to select"
                )
                .hideFlags(ItemFlag.HIDE_ENCHANTS)
                .onClick(e -> {
                    if (data != null) {
                        data.setRole(PlayerRole.SPECTATOR);
                        player.sendMessage("\u00A7bYou are now set as a \u00A7lSpectator\u00A7b!");
                        new LobbyGui(player).open();
                    }
                });

        if (currentRole == PlayerRole.SPECTATOR) {
            spectatorIcon.enchant(Enchantment.DURABILITY, 1);
        }

        sheet.setIcon(15, spectatorIcon);

        return sheet;
    }
}
