package host.plas.furyspeedrunning.enums;

/**
 * High-level match type from config ({@code game.mode}).
 * Distinct from {@link org.bukkit.GameMode} (survival/creative).
 */
public enum PluginGameMode {
    /** Shared world, imposter / timer / vote flow. */
    COOP,
    /** Two runners, two seed pairs, first dragon wins; no imposter mechanics. */
    VERSUS
}
