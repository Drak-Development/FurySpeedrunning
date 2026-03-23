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
        getLobbySpawnX();
        getLobbySpawnY();
        getLobbySpawnZ();
        getLobbySpawnYaw();
        getLobbySpawnPitch();
        getLobbySpawnWorld();
        getTemplatePreGenRadius();
        getMaxPlayers();
        getGameStartCountdown();
        getPostWinDelay();
        getLootStringChance();
        getLootObsidianChance();
        getMobPearlDropRate();
        getMobBlazeRodDropRate();
    }

    @SuppressWarnings("unchecked")
    public List<Long> getSeeds() {
        reloadResource();
        // Curated seeds for 1.16 speedrunning — good structure proximity and loot
        List<Long> defaults = Arrays.asList(
                -4530634556500121041L, // Village + ruined portal near spawn
                6440029834698013982L,  // Close stronghold, nether fortress
                -1894252613L,          // Village at spawn with blacksmith
                2483313382402348964L,  // Dual village spawn, close fortress
                -7866608132722458683L, // Close bastion + fortress combo
                1935762657302024089L,  // Shipwreck + village near spawn
                -3294725893620991126L, // Double village, close stronghold
                5765923602036276498L,  // Ruined portal, village, close end
                -8767654563534078661L, // Bastion + fortress close, village
                725729218939607560L    // Good nether, close structures
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

    public double getLobbySpawnYaw() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.yaw", 0.0d);
    }

    public double getLobbySpawnPitch() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.pitch", 0.0d);
    }

    public String getLobbySpawnWorld() {
        reloadResource();
        return getOrSetDefault("lobby.spawn.world", "");
    }

    public int getTemplatePreGenRadius() {
        reloadResource();
        return getOrSetDefault("world.template-pre-gen-radius", 2000);
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

    public double getLootStringChance() {
        reloadResource();
        return getOrSetDefault("loot.string-chance", 0.40d);
    }

    public double getLootObsidianChance() {
        reloadResource();
        return getOrSetDefault("loot.obsidian-chance", 0.50d);
    }

    public double getMobPearlDropRate() {
        reloadResource();
        return getOrSetDefault("loot.mob-pearl-drop-rate", 0.75d);
    }

    public double getMobBlazeRodDropRate() {
        reloadResource();
        return getOrSetDefault("loot.mob-blaze-rod-drop-rate", 0.80d);
    }
}
