package host.plas.furyspeedrunning.gui;

import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.PlayerRole;
import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VoteGui extends ScreenInstance {

    public VoteGui(Player player) {
        super(player, FuryGuiType.VOTE, buildSheet(player), true);
    }

    private static InventorySheet buildSheet(Player voter) {
        InventorySheet sheet = InventorySheet.empty(27);

        // Fill background
        Icon filler = new Icon(Material.GRAY_STAINED_GLASS_PANE).setName(" ");
        for (int i = 0; i < 27; i++) {
            sheet.setIcon(i, filler);
        }

        // Get all players + hunters (potential vote targets)
        List<Player> candidates = new ArrayList<>();
        candidates.addAll(PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER));
        candidates.addAll(PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.HUNTER));

        // Remove the voter from the candidate list
        candidates.removeIf(p -> p.getUniqueId().equals(voter.getUniqueId()));

        if (candidates.isEmpty()) {
            Icon noCandidates = new Icon(Material.BARRIER)
                    .setName("\u00A7cNo players to vote for")
                    .setLore(" ", "\u00A77There are no other players in the game.");
            sheet.setIcon(13, noCandidates);
            return sheet;
        }

        UUID currentVote = GameManager.getVote(voter.getUniqueId());

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int slotIndex = 0;

        for (Player candidate : candidates) {
            if (slotIndex >= slots.length) break;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(candidate);
                meta.setDisplayName("\u00A7e\u00A7l" + candidate.getName());
                skull.setItemMeta(meta);
            }

            boolean isCurrentVote = currentVote != null && currentVote.equals(candidate.getUniqueId());

            Icon icon = new Icon(skull)
                    .setLore(
                            " ",
                            "\u00A77Click to vote for this player",
                            "\u00A77as the \u00A7c\u00A7lImposter\u00A77.",
                            " ",
                            isCurrentVote ? "\u00A7a\u25B6 Your current vote" : "\u00A7eClick to select"
                    )
                    .onClick(e -> {
                        GameManager.setVote(voter.getUniqueId(), candidate.getUniqueId());
                        voter.sendMessage("\u00A7aYou voted for \u00A7e" + candidate.getName() + " \u00A7aas the Imposter!");
                        voter.closeInventory();
                    });

            sheet.setIcon(slots[slotIndex], icon);
            slotIndex++;
        }

        return sheet;
    }
}
