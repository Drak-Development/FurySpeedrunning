package host.plas.furyspeedrunning.data;

import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerManager {
    private static final Map<UUID, PlayerData> PLAYERS = new ConcurrentHashMap<>();

    public static PlayerData getOrCreatePlayer(Player player) {
        return PLAYERS.computeIfAbsent(player.getUniqueId(), PlayerData::new);
    }

    public static PlayerData getPlayer(UUID uuid) {
        return PLAYERS.get(uuid);
    }

    public static PlayerData getPlayer(Player player) {
        return PLAYERS.get(player.getUniqueId());
    }

    public static void removePlayer(UUID uuid) {
        PLAYERS.remove(uuid);
    }

    public static List<PlayerData> getPlayersByRole(PlayerRole role) {
        return PLAYERS.values().stream()
                .filter(data -> data.getRole() == role)
                .collect(Collectors.toList());
    }

    public static List<PlayerData> getOnlinePlayers() {
        return PLAYERS.values().stream()
                .filter(PlayerData::isOnline)
                .collect(Collectors.toList());
    }

    public static List<Player> getOnlineBukkitPlayersByRole(PlayerRole role) {
        return getPlayersByRole(role).stream()
                .filter(PlayerData::isOnline)
                .map(PlayerData::getPlayer)
                .collect(Collectors.toList());
    }

    public static void clear() {
        PLAYERS.clear();
    }

    /** All cached {@link PlayerData} (online or offline during an active match). */
    public static Collection<PlayerData> getRegisteredPlayers() {
        return Collections.unmodifiableCollection(new ArrayList<>(PLAYERS.values()));
    }
}
