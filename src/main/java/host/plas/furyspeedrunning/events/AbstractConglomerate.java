package host.plas.furyspeedrunning.events;

import gg.drak.thebase.events.BaseEventHandler;
import host.plas.bou.events.ListenerConglomerate;
import host.plas.furyspeedrunning.FurySpeedrunning;
import org.bukkit.Bukkit;

public class AbstractConglomerate implements ListenerConglomerate {
    public AbstractConglomerate() {
        register();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, FurySpeedrunning.getInstance());
        BaseEventHandler.bake(this, FurySpeedrunning.getInstance());
        FurySpeedrunning.getInstance().logInfo("Registered listeners for: &c" + this.getClass().getSimpleName());
    }
}
