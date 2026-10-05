package dev.medirian.module.impl.misc;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.GameView;
import dev.medirian.setting.BooleanSetting;

/** Current server address and online player count. */
public final class ServerInfoModule extends Module {

    private final BooleanSetting showPlayers;
    private final BooleanSetting showPing;

    public ServerInfoModule() {
        super("serverinfo", "Server Info", Category.MISC, "Shows the server address and player count.");
        showPlayers = add(new BooleanSetting("showPlayers", "Show player count", true));
        showPing = add(new BooleanSetting("showPing", "Show ping", false));
        hud(new TextHudElement(this, Anchor.TOP_LEFT, 4, 147) {
            @Override
            protected void collect(Lines out, boolean editor) {
                GameView game = Medirian.get().game();
                if (!game.inWorld() || game.isSingleplayer()) {
                    out.add(I18n.tr("hud.server", "Server"), I18n.tr("hud.server.singleplayer", "Singleplayer"));
                    return;
                }
                out.add(I18n.tr("hud.server", "Server"), game.serverAddress());
                if (showPlayers.on() && game.onlinePlayers() >= 0) {
                    out.add(I18n.tr("hud.server.players", "Players"), String.valueOf(game.onlinePlayers()));
                }
                if (showPing.on() && game.ping() >= 0) {
                    out.add("Ping", game.ping() + " ms");
                }
            }

            @Override
            protected long refreshIntervalMs() {
                return 1000;
            }

            @Override
            public boolean hasContent() {
                return Medirian.get().game().inWorld();
            }
        });
    }
}
