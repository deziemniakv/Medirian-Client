package dev.medirian.module.impl.hud;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.module.Category;
import dev.medirian.module.Module;

/** Latency to the current server (hidden in singleplayer). */
public final class PingModule extends Module {

    public PingModule() {
        super("ping", "Ping", Category.HUD, "Shows your latency to the server.");
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 21) {
            @Override
            protected void collect(Lines out, boolean editor) {
                int ping = Medirian.get().game().ping();
                out.add("Ping", ping < 0 ? "-" : ping + " ms");
            }

            @Override
            protected long refreshIntervalMs() {
                return 1000;
            }

            @Override
            public boolean hasContent() {
                return Medirian.get().game().ping() >= 0;
            }
        });
    }
}
