package host.plas.furyspeedrunning.data;

import host.plas.furyspeedrunning.enums.PlayerRole;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@Getter @Setter
public class PlayerData {
    private final UUID uuid;
    private PlayerRole role;

    /**
     * 0 or 1 for {@link host.plas.furyspeedrunning.enums.PluginGameMode#VERSUS} runners; null in coop or spectators.
     */
    private Integer versusRunnerIndex;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.role = PlayerRole.PLAYER;
    }

    /** Coop and unset versus use bundle 0 for respawn routing. */
    public int getVersusRunnerIndexOrZero() {
        return versusRunnerIndex != null ? versusRunnerIndex : 0;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public void saveAndUnload() {
        PlayerManager.removePlayer(uuid);
    }
}
