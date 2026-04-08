package host.plas.furyspeedrunning.gui;

import host.plas.bou.gui.GuiType;
import lombok.Getter;

@Getter
public enum FuryGuiType implements GuiType {
    LOBBY("\u00A76\u00A7lGame Menu"),
    SPECTATOR("\u00A7b\u00A7lSpectate Players"),
    VOTE("\u00A7c\u00A7lVote for Imposter"),
    ;

    private final String title;

    FuryGuiType(String title) {
        this.title = title;
    }
}
