package host.plas.furyspeedrunning;

import host.plas.bou.BetterPlugin;
import host.plas.furyspeedrunning.config.MainConfig;
import host.plas.furyspeedrunning.events.MainListener;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public final class FurySpeedrunning extends BetterPlugin {
    @Getter @Setter
    private static FurySpeedrunning instance;
    @Getter @Setter
    private static MainConfig mainConfig;

    @Getter @Setter
    private static MainListener mainListener;

    public FurySpeedrunning() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        // Plugin startup logic
        setInstance(this); // Set the instance of the plugin. // For use in other classes.

        setMainConfig(new MainConfig()); // Instantiate the main config and set it.

        setMainListener(new MainListener()); // Instantiate the main listener and set it.
    }

    @Override
    public void onBaseDisable() {
        // Plugin shutdown logic
    }
}
