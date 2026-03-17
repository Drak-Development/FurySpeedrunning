package host.plas.furyspeedrunning.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.furyspeedrunning.FurySpeedrunning;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", FurySpeedrunning.getInstance(), false);
    }

    @Override
    public void init() {
        getSeeds();
        getWorldPrefix();
        getPreGenerateRadius();
        getLobbySpawnX();
        getLobbySpawnY();
        getLobbySpawnZ();
        getMaxPlayers();
        getGameStartCountdown();
        getPostWinDelay();
    }

    @SuppressWarnings("unchecked")
    public List<Long> getSeeds() {
        reloadResource();
        List<Long> defaults = Arrays.asList(
                123456789L,
                987654321L,
                -1234567890L,
                5555555555L,
                -9876543210L
        );
        Object raw = getOrSetDefault("seeds", defaults);
        if (raw instanceof List) {
            return ((List<?>) raw).stream()
                    .map(o -> {
                        if (o instanceof Long) return (Long) o;
                        if (o instanceof Integer) return ((Integer) o).longValue();
                        return Long.parseLong(o.toString());
                    })
                    .collect(Collectors.toList());
        }
        return defaults;
    }

    public String getWorldPrefix() {
        reloadResource();
        return getOrSetDefault("world.prefix", "speedrun");
    }

    public int getPreGenerateRadius() {
        reloadResource();
        return getOrSetDefault("world.pre-generate-radius", 8);
    }

    public double getLobbySpawnX() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.x", 0.5d);
    }

    public double getLobbySpawnY() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.y", 65.0d);
    }

    public double getLobbySpawnZ() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.z", 0.5d);
    }

    public int getMaxPlayers() {
        reloadResource();
        return getOrSetDefault("game.max-players", 20);
    }

    public int getGameStartCountdown() {
        reloadResource();
        return getOrSetDefault("game.start-countdown-seconds", 5);
    }

    public int getPostWinDelay() {
        reloadResource();
        return getOrSetDefault("game.post-win-delay-seconds", 10);
    }
}
