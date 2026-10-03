package dev.meridian.module.impl.hud;

import dev.meridian.core.Meridian;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.TextHudElement;
import dev.meridian.module.Category;
import dev.meridian.module.Module;

/** Latency to the current server (hidden in singleplayer). */
public final class PingModule extends Module {

    public PingModule() {
        super("ping", "Ping", Category.HUD, "Shows your latency to the server.");
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 22) {
            @Override
            protected void collect(Lines out, boolean editor) {
                int ping = Meridian.get().game().ping();
                out.add("Ping", ping < 0 ? "-" : ping + " ms");
            }

            @Override
            protected long refreshIntervalMs() {
                return 1000;
            }

            @Override
            public boolean hasContent() {
                return Meridian.get().game().ping() >= 0;
            }
        });
    }
}
