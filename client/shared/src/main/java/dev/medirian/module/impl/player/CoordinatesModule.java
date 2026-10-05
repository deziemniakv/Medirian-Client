package dev.medirian.module.impl.player;

import dev.medirian.core.Medirian;
import dev.medirian.hud.Anchor;
import dev.medirian.hud.TextHudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Category;
import dev.medirian.module.Module;
import dev.medirian.module.impl.world.DirectionModule;
import dev.medirian.platform.Capability;
import dev.medirian.platform.PlayerView;
import dev.medirian.setting.BooleanSetting;
import dev.medirian.setting.ModeSetting;
import dev.medirian.setting.NumberSetting;
import dev.medirian.util.Format;

/** Player position with optional facing, biome and Nether/Overworld conversion. */
public final class CoordinatesModule extends Module {

    /** Line layout. */
    public enum Layout { LINES, COMPACT }

    private final ModeSetting<Layout> layout;
    private final NumberSetting decimals;
    private final BooleanSetting showFacing;
    private final BooleanSetting showBiome;
    private final BooleanSetting showConverted;

    public CoordinatesModule() {
        super("coordinates", "Coordinates", Category.PLAYER, "Shows your position in the world.");
        enableByDefault();
        layout = add(new ModeSetting<Layout>("layout", "Layout", Layout.LINES));
        decimals = add(new NumberSetting("decimals", "Decimals", 0, 0, 2, 1));
        showFacing = add(new BooleanSetting("showFacing", "Show facing", true));
        showBiome = add(new BooleanSetting("showBiome", "Show biome", false)
                .visibleWhen(() -> Medirian.get().platform().supports(Capability.BIOME)));
        showConverted = add(new BooleanSetting("showConverted", "Show Nether/Overworld coordinates", false));

        hud(new TextHudElement(this, Anchor.MIDDLE_LEFT, 4, 0) {
            @Override
            protected void collect(Lines out, boolean editor) {
                PlayerView player = Medirian.get().game().player();
                if (player == null) {
                    out.add("XYZ", "-");
                    return;
                }
                int d = decimals.intValue();
                String x = Format.decimals(player.x(), d);
                String y = Format.decimals(player.y(), d);
                String z = Format.decimals(player.z(), d);
                if (layout.is(Layout.COMPACT)) {
                    out.add("XYZ", x + ", " + y + ", " + z);
                } else {
                    out.add("X", x);
                    out.add("Y", y);
                    out.add("Z", z);
                }
                if (showFacing.on()) {
                    out.add(I18n.tr("hud.coordinates.facing", "Facing"), DirectionModule.facing(player.yaw()));
                }
                if (showBiome.on()) {
                    String biome = Medirian.get().game().biome();
                    if (biome != null) {
                        out.add(I18n.tr("hud.biome", "Biome"), biome);
                    }
                }
                if (showConverted.on()) {
                    PlayerView.Dimension dimension = player.dimension();
                    if (dimension == PlayerView.Dimension.NETHER) {
                        out.add(I18n.tr("hud.coordinates.overworld", "Overworld"),
                                Format.decimals(player.x() * 8, 0) + ", " + Format.decimals(player.z() * 8, 0));
                    } else if (dimension == PlayerView.Dimension.OVERWORLD) {
                        out.add(I18n.tr("hud.coordinates.nether", "Nether"),
                                Format.decimals(player.x() / 8, 0) + ", " + Format.decimals(player.z() / 8, 0));
                    }
                }
            }

            @Override
            public boolean hasContent() {
                return Medirian.get().game().player() != null;
            }
        });
    }
}
