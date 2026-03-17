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

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.role = PlayerRole.PLAYER;
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
