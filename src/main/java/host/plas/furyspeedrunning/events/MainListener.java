package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import host.plas.furyspeedrunning.world.LobbyManager;
import host.plas.furyspeedrunning.world.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MainListener extends AbstractConglomerate {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = PlayerManager.getOrCreatePlayer(player);

        if (GameManager.getState() == GameState.LOBBY) {
            LobbyManager.sendToLobby(player);
            LobbyManager.giveLobbyItems(player);
        } else if (GameManager.getState() == GameState.PLAYING) {
            // Reconnecting during a game
            switch (data.getRole()) {
                case PLAYER:
                    GameManager.setupPlayerRole(player);
                    break;
                case HUNTER:
                    GameManager.setupHunterRole(player);
                    break;
                case SPECTATOR:
                    GameManager.setupSpectatorRole(player);
                    break;
            }
            if (WorldManager.getOverworld() != null) {
                player.teleport(WorldManager.getOverworld().getSpawnLocation().add(0.5, 0, 0.5));
            }
            // Re-apply spectator visibility
            GameManager.applySpectatorVisibility();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = PlayerManager.getPlayer(player);

        if (data != null) {
            if (GameManager.getState() == GameState.LOBBY) {
                data.saveAndUnload();
            }
            // During PLAYING, keep data so they can reconnect
        }
    }
}
