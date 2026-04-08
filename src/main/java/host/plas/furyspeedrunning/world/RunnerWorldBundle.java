package host.plas.furyspeedrunning.world;

import lombok.Getter;
import org.bukkit.World;

/**
 * One runner's overworld + nether + end (single seed pair).
 */
@Getter
public class RunnerWorldBundle {
    private final int runnerIndex;
    private final World overworld;
    private final World nether;
    private final World end;
    private final long overworldSeed;
    private final long netherSeed;

    public RunnerWorldBundle(int runnerIndex, World overworld, World nether, World end,
                             long overworldSeed, long netherSeed) {
        this.runnerIndex = runnerIndex;
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;
        this.overworldSeed = overworldSeed;
        this.netherSeed = netherSeed;
    }

    public boolean containsWorld(World world) {
        if (world == null) return false;
        return world.equals(overworld) || world.equals(nether) || world.equals(end);
    }
}
