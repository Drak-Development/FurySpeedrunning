package host.plas.furyspeedrunning.gui;

import host.plas.bou.gui.GuiType;
import lombok.Getter;

@Getter
public enum FuryGuiType implements GuiType {
    LOBBY("\u00A76\u00A7lGame Menu"),
    SPECTATOR("\u00A7b\u00A7lSpectate Players"),
    ;

    private final String title;

    FuryGuiType(String title) {
        this.title = title;
    }
}
