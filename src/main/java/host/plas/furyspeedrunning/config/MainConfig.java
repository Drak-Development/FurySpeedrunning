package host.plas.furyspeedrunning.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.furyspeedrunning.FurySpeedrunning;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", FurySpeedrunning.getInstance(), false);
    }

    @Override
    public void init() {

    }
}
