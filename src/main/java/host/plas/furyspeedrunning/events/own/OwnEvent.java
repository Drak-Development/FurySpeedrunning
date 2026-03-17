package host.plas.furyspeedrunning.events.own;

import gg.drak.thebase.events.components.BaseEvent;
import host.plas.bou.BukkitOfUtils;
import host.plas.furyspeedrunning.FurySpeedrunning;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OwnEvent extends BaseEvent {
    public OwnEvent() {
        super();
    }

    public FurySpeedrunning getPlugin() {
        return FurySpeedrunning.getInstance();
    }

    public BukkitOfUtils getBou() {
        return BukkitOfUtils.getInstance();
    }
}
