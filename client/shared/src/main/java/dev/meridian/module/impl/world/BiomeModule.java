package dev.meridian.module.impl.world;

import dev.meridian.core.Meridian;
import dev.meridian.hud.Anchor;
import dev.meridian.hud.TextHudElement;
import dev.meridian.i18n.I18n;
import dev.meridian.module.Category;
import dev.meridian.module.Module;
import dev.meridian.platform.Capability;

/** Name of the biome at the player's position. */
public final class BiomeModule extends Module {

    public BiomeModule() {
        super("biome", "Biome Display", Category.WORLD, "Shows the biome you are standing in.");
        requires(Capability.BIOME);
        hud(new TextHudElement(this, Anchor.MIDDLE_LEFT, 4, 48) {
            @Override
            protected void collect(Lines out, boolean editor) {
                String biome = Meridian.get().game().biome();
                out.add(I18n.tr("hud.biome", "Biome"), biome == null ? "-" : biome);
            }

            @Override
            protected long refreshIntervalMs() {
                return 500;
            }

            @Override
            public boolean hasContent() {
                return Meridian.get().game().biome() != null;
            }
        });
    }
}
