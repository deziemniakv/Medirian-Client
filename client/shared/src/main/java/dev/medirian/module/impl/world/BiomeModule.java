package dev.medirian.module.impl.world;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.platform.Capability;

/** Name of the biome at the player's position. */
public final class BiomeModule extends Module {

    public BiomeModule() {
        super("biome", "Biome Display", Category.WORLD, "Shows the biome you are standing in.");
        requires(Capability.BIOME);
        hud(new TextHudElement(this, Anchor.MIDDLE_LEFT, 4, 50) {
            @Override
            protected void collect(Lines out, boolean editor) {
                String biome = Medirian.get().game().biome();
                out.add(I18n.tr("hud.biome", "Biome"), biome == null ? "-" : biome);
            }

            @Override
            protected long refreshIntervalMs() {
                return 500;
            }

            @Override
            public boolean hasContent() {
                return Medirian.get().game().biome() != null;
            }
        });
    }
}
