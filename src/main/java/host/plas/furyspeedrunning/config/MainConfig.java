package host.plas.furyspeedrunning.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.furyspeedrunning.FurySpeedrunning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", FurySpeedrunning.getInstance(), false);
    }

    @Override
    public void init() {
        getSeedPairs();
        getWorldPrefix();
        getLobbySpawnX();
        getLobbySpawnY();
        getLobbySpawnZ();
        getLobbySpawnYaw();
        getLobbySpawnPitch();
        getLobbySpawnWorld();
        getPreGenRadius();
        getMaxPlayers();
        getGameStartCountdown();
        getPostWinDelay();
        getLootStringChance();
        getLootObsidianChance();
        getMobPearlDropRate();
        getMobBlazeRodDropRate();
        getPiglinPearlRate();
        getPlayedSeedIndices();
    }

    @SuppressWarnings("unchecked")
    public List<SeedPair> getSeedPairs() {
        reloadResource();
        // Curated seed pairs for 1.16 speedrunning — overworld seed is also used for the end
        List<Map<String, Long>> defaults = Arrays.asList(
                seedEntry(-4530634556500121041L, 3257840388504953787L),
                seedEntry(6440029834698013982L, -7789039675687883082L),
                seedEntry(-1894252613L, 8834246424662967901L),
                seedEntry(2483313382402348964L, -2223549622041829018L),
                seedEntry(-7866608132722458683L, 5765438034165498430L),
                seedEntry(1935762657302024089L, -6601736963498508847L),
                seedEntry(-3294725893620991126L, 1122334455667788990L),
                seedEntry(5765923602036276498L, -8988776655443322110L),
                seedEntry(-8767654563534078661L, 4466778899001122334L),
                seedEntry(725729218939607560L, -3344556677889900112L)
        );
        Object raw = getOrSetDefault("seeds", defaults);
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            List<SeedPair> pairs = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) entry;
                    long overworld = toLong(map.get("overworld"));
                    long nether = toLong(map.get("nether"));
                    pairs.add(new SeedPair(overworld, nether));
                }
            }
            if (!pairs.isEmpty()) return pairs;
        }
        return defaults.stream()
                .map(m -> new SeedPair(m.get("overworld"), m.get("nether")))
                .collect(Collectors.toList());
    }

    private static Map<String, Long> seedEntry(long overworld, long nether) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("overworld", overworld);
        map.put("nether", nether);
        return map;
    }

    private static long toLong(Object o) {
        if (o instanceof Long) return (Long) o;
        if (o instanceof Integer) return ((Integer) o).longValue();
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
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

    public int getPreGenRadius() {
        reloadResource();
        return getOrSetDefault("world.pre-gen-radius", 1200);
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
        return getOrSetDefault("loot.mob-pearl-drop-rate", 0.90d);
    }

    public double getMobBlazeRodDropRate() {
        reloadResource();
        return getOrSetDefault("loot.mob-blaze-rod-drop-rate", 0.55d);
    }

    public double getPiglinPearlRate() {
        reloadResource();
        return getOrSetDefault("loot.piglin-pearl-rate", 0.20d);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getPlayedSeedIndices() {
        reloadResource();
        List<Integer> defaults = new ArrayList<>();
        Object raw = getOrSetDefault("played-seeds", defaults);
        if (raw instanceof List) {
            return ((List<?>) raw).stream()
                    .map(o -> {
                        if (o instanceof Integer) return (Integer) o;
                        if (o instanceof Number) return ((Number) o).intValue();
                        return Integer.parseInt(o.toString());
                    })
                    .collect(Collectors.toList());
        }
        return defaults;
    }

    public void addPlayedSeedIndex(int index) {
        List<Integer> played = new ArrayList<>(getPlayedSeedIndices());
        if (!played.contains(index)) {
            played.add(index);
            getOrSetDefault("played-seeds", played);
        }
    }
}
