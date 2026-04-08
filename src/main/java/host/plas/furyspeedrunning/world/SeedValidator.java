package host.plas.furyspeedrunning.world;

import java.util.Random;

/**
 * FSG-style (Filtered Seed Glitchless) seed checks using the same structure spacing the server applies
 * via {@link WorldGenModifier} (keys: village, fortress, bastion_remnant, ruined_portal, shipwreck).
 * <p>
 * Criteria follow the spirit of <a href="https://github.com/AndyNovo/filteredseed">AndyNovo/filteredseed</a>
 * (legacy v0.5 C tool). Minecraft 1.21.x still uses the classic {@code seed + salt + region*mult} region RNG
 * for these structures; spacing/separation here must match the values applied in
 * {@link WorldGenModifier} or validation will not reflect in-game generation.
 * <p>
 * Checks (overworld region 0,0 and nether regions as noted):
 * <ul>
 *   <li>Nether: bastion in +/+ within 8 chunks of origin; fortress in -/+ or +/- near origin</li>
 *   <li>Ruined portal in +/+ between 80–144 blocks and {@link #isPortalAboveGround} heuristic</li>
 *   <li>Village in +/+ within 96 blocks (blacksmith still verified after chunk generation if needed)</li>
 * </ul>
 */
public class SeedValidator {

    // Minecraft region coordinate multipliers (unchanged across 1.16–1.21 for these structures)
    private static final long REGION_X_MULT = 341873128712L;
    private static final long REGION_Z_MULT = 132897987541L;

    // Nether: same spacing/separation as WorldGenModifier "fortress" / "bastion_remnant" entries
    private static final int NETHER_SPACING = 15;
    private static final int NETHER_SEPARATION = 3;
    private static final long NETHER_SALT = 30084232L;

    // Overworld: match WorldGenModifier "village", "ruined_portal", "shipwreck" entries
    private static final int VILLAGE_SPACING = 16;
    private static final int VILLAGE_SEPARATION = 4;
    private static final long VILLAGE_SALT = 10387312L;

    private static final int PORTAL_SPACING = 20;
    private static final int PORTAL_SEPARATION = 6;
    private static final long PORTAL_SALT = 34222645L;

    private static final int SHIPWRECK_SPACING = 14;
    private static final int SHIPWRECK_SEPARATION = 4;
    private static final long SHIPWRECK_SALT = 165745295L;

    /**
     * Finds a valid FSG seed. Loops until one is found (no attempt limit).
     * Should be called off the main thread.
     */
    public static long findFilteredSeed() {
        Random rng = new Random();
        while (true) {
            long seed = rng.nextLong();
            if (isValidFSG(seed)) return seed;
        }
    }

    /**
     * Validates a seed against FSG criteria using pure math.
     */
    public static boolean isValidFSG(long seed) {
        // 1. Nether: bastion in +/+, fortress in +/- or -/+
        if (checkNether(seed) == 0) return false;

        // 2. Ruined portal in pos/pos between 80-144 blocks
        int[] portalChunks = getRegionStructureChunks(seed, PORTAL_SALT, PORTAL_SPACING, PORTAL_SEPARATION, 0, 0);
        int portalX = portalChunks[0] * 16;
        int portalZ = portalChunks[1] * 16;
        if (portalX <= 80 || portalZ <= 80 || portalX >= 144 || portalZ >= 144) return false;

        // 3. Portal must be above ground (Y threshold check from FSG)
        if (!isPortalAboveGround(seed, portalChunks[0], portalChunks[1])) return false;

        // 4. Village in pos/pos within 96 blocks (required — blacksmith check happens post-generation)
        if (!isStructureInRange(seed, VILLAGE_SALT, VILLAGE_SPACING, VILLAGE_SEPARATION, 96)) return false;

        return true;
    }

    /**
     * Returns the village chunk coordinates for a seed (region 0,0).
     * Used by WorldTemplateManager to scan for blacksmith buildings after world creation.
     */
    public static int[] getVillageChunks(long seed) {
        return getRegionStructureChunks(seed, VILLAGE_SALT, VILLAGE_SPACING, VILLAGE_SEPARATION, 0, 0);
    }

    /**
     * Gets chunk position for a structure in a given region.
     * Returns [chunkX, chunkZ] in absolute chunk coordinates.
     */
    private static int[] getRegionStructureChunks(long seed, long salt, int spacing, int separation, int regionX, int regionZ) {
        long regionSeed = seed + salt + regionX * REGION_X_MULT + regionZ * REGION_Z_MULT;
        Random rng = new Random(regionSeed);
        int range = spacing - separation;
        int offsetX = rng.nextInt(range);
        int offsetZ = rng.nextInt(range);
        return new int[]{regionX * spacing + offsetX, regionZ * spacing + offsetZ};
    }

    /**
     * Check if an overworld structure in region (0,0) falls within maxBlocks of origin.
     */
    private static boolean isStructureInRange(long seed, long salt, int spacing, int separation, int maxBlocks) {
        int[] chunks = getRegionStructureChunks(seed, salt, spacing, separation, 0, 0);
        int blockX = chunks[0] * 16;
        int blockZ = chunks[1] * 16;
        return blockX >= 0 && blockX <= maxBlocks && blockZ >= 0 && blockZ <= maxBlocks;
    }

    /**
     * Checks nether structures match FSG criteria.
     * Returns fortress quadrant (-1 for -/+, 1 for +/-) or 0 if invalid.
     */
    private static int checkNether(long seed) {
        int range = NETHER_SPACING - NETHER_SEPARATION; // 23

        // pos/pos (region 0,0): must be bastion within 8 chunks of origin
        Random rng = new Random(seed + NETHER_SALT);
        int cx = rng.nextInt(range);
        int cz = rng.nextInt(range);
        boolean isBastion = rng.nextInt(5) >= 2;
        if (!isBastion || cx > 8 || cz > 8) return 0;

        // neg/pos (region -1, 0): check for fortress close to origin
        rng = new Random(seed + NETHER_SALT - REGION_X_MULT);
        cx = rng.nextInt(range);
        cz = rng.nextInt(range);
        boolean isFortress = rng.nextInt(5) < 2;
        if (isFortress && cx >= 8 && cz <= 8) return -1;

        // pos/neg (region 0, -1): check for fortress close to origin
        rng = new Random(seed + NETHER_SALT - REGION_Z_MULT);
        cx = rng.nextInt(range);
        cz = rng.nextInt(range);
        isFortress = rng.nextInt(5) < 2;
        if (isFortress && cx <= 8 && cz >= 8) return 1;

        return 0;
    }

    /**
     * Checks if a ruined portal will generate above ground (visible lava).
     * Ports the decoration seed + Y-threshold check from the FSG C code.
     */
    private static boolean isPortalAboveGround(long seed, int portalChunkX, int portalChunkZ) {
        Random rng = new Random(seed);
        long carveA = rng.nextLong();
        long carveB = rng.nextLong();
        long portalSeed = ((long) portalChunkX * carveA) ^ ((long) portalChunkZ * carveB) ^ seed;
        rng = new Random(portalSeed);
        float oceanY = rng.nextFloat();
        // oceanY >= 0.5 means above ground portal with lava
        return oceanY >= 0.5f;
    }
}
