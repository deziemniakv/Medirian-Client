package dev.meridian.module.impl.hud;

import dev.meridian.core.Meridian;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.TextHudElement;
import dev.meridian.i18n.I18n;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.setting.BooleanSetting;
import dev.meridian.util.Format;

/** Time spent in the current world/server and since the game was launched. */
public final class SessionInfoModule extends Module {

    private final BooleanSetting showTotal;

    public SessionInfoModule() {
        super("sessioninfo", "Session Info", Category.HUD, "Shows how long you have been playing.");
        showTotal = add(new BooleanSetting("showTotal", "Show time since launch", true));
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 112) {
            @Override
            protected void collect(Lines out, boolean editor) {
                Meridian meridian = Meridian.get();
                long now = System.currentTimeMillis();
                long joined = meridian.worldJoinedAtMs();
                out.add(I18n.tr("hud.session", "Session"), joined == 0 ? "-" : Format.duration(now - joined));
                if (showTotal.on()) {
                    out.add(I18n.tr("hud.session.total", "Playtime"), Format.duration(now - meridian.launchedAtMs()));
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 1000;
            }
        });
    }
}
