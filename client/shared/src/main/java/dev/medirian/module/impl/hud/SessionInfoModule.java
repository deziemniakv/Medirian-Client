package dev.medirian.module.impl.hud;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.util.Format;

/** Time spent in the current world/server and since the game was launched. */
public final class SessionInfoModule extends Module {

    private final BooleanSetting showTotal;

    public SessionInfoModule() {
        super("sessioninfo", "Session Info", Category.HUD, "Shows how long you have been playing.");
        showTotal = add(new BooleanSetting("showTotal", "Show time since launch", true));
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 112) {
            @Override
            protected void collect(Lines out, boolean editor) {
                Medirian medirian = Medirian.get();
                long now = System.currentTimeMillis();
                long joined = medirian.worldJoinedAtMs();
                out.add(I18n.tr("hud.session", "Session"), joined == 0 ? "-" : Format.duration(now - joined));
                if (showTotal.on()) {
                    out.add(I18n.tr("hud.session.total", "Playtime"), Format.duration(now - medirian.launchedAtMs()));
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 1000;
            }
        });
    }
}
