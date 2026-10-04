package dev.meridian.ui;

import dev.meridian.account.MeridianAccountService;
import dev.meridian.core.Meridian;
import dev.meridian.cosmetics.Cosmetic;
import dev.meridian.cosmetics.CosmeticType;
import dev.meridian.cosmetics.CosmeticsManager;
import dev.meridian.i18n.I18n;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;
import dev.meridian.ui.widget.Button;

import java.util.List;

/**
 * Cosmetics: pick the cape you wear. Clicking an owned cape equips it; the preview shows the
 * selected one. Only offered in versions that render capes.
 */
public final class CosmeticsScreen extends MeridianScreen {

    /** Front of the cape in the 64×32 cape layout (fractions of the texture). */
    private static final float U0 = 1 / 64f;
    private static final float V0 = 1 / 32f;
    private static final float U1 = 11 / 64f;
    private static final float V1 = 17 / 32f;
    private static final int CARD_W = 62;
    private static final int CARD_H = 92;
    private static final int GAP = 8;

    private float px;
    private float py;
    private float pw;
    private float ph;
    private float gridX;
    private float gridY;
    private int columns;
    private Button doneButton;
    /** Selected cape in the grid (null = "None"). */
    private Cosmetic selected;

    public CosmeticsScreen(MeridianScreen parent) {
        super(parent);
        selected = Meridian.get().cosmetics().equipped(CosmeticType.CAPE);
    }

    @Override
    protected void init() {
        pw = Math.min(560, width - 32);
        ph = Math.min(330, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        gridX = px + 14;
        gridY = py + 40;
        columns = Math.max(1, (int) ((pw - 190 - 14) / (CARD_W + GAP)));
        doneButton = new Button(parent != null ? I18n.tr("ui.back", "Back") : I18n.tr("ui.done", "Done"),
                Button.Style.SECONDARY, this::close).bounds(px + pw - 164, py + ph - 26, 150, 16);
    }

    private List<Cosmetic> capes() {
        return Meridian.get().cosmetics().catalogue(CosmeticType.CAPE);
    }

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        Theme theme = Theme.current();
        CosmeticsManager cosmetics = Meridian.get().cosmetics();
        renderBackdrop(g);
        UiDraw.shadow(g, px, py, pw, ph, 8, 4);
        UiDraw.roundRectBordered(g, px, py, pw, ph, 8, theme.panel, theme.border);
        g.text("§l" + I18n.tr("cosmetics.title", "Cosmetics"), px + 14, py + 13, theme.text, false);
        g.text(I18n.tr("cosmetics.capes", "Capes"), px + 14, py + 25, theme.textDim, false);

        List<Cosmetic> capes = capes();
        Cosmetic equipped = cosmetics.equipped(CosmeticType.CAPE);
        for (int i = 0; i <= capes.size(); i++) {
            Cosmetic cape = i == 0 ? null : capes.get(i - 1);
            float x = gridX + (i % columns) * (CARD_W + GAP);
            float y = gridY + (i / columns) * (CARD_H + GAP);
            renderCard(g, theme, cosmetics, cape, cape == equipped, x, y, mx, my);
        }

        renderPreview(g, theme, cosmetics);
        doneButton.render(g, mx, my);
    }

    private void renderCard(Gfx g, Theme theme, CosmeticsManager cosmetics, Cosmetic cape, boolean equipped,
                            float x, float y, float mx, float my) {
        boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        boolean owned = cape == null || cosmetics.owns(cape);
        boolean isSelected = cape == selected;
        int border = equipped ? theme.accent : isSelected ? theme.borderStrong : theme.border;
        UiDraw.roundRectBordered(g, x, y, CARD_W, CARD_H, 5, hover ? theme.elevated : theme.surface, border);
        int previewW = 36;
        int previewH = 58;
        int ix = (int) (x + (CARD_W - previewW) / 2f);
        int iy = (int) y + 7;
        if (cape == null) {
            UiDraw.roundRect(g, ix, iy, previewW, previewH, 3, Colors.withAlpha(theme.border, 0x80));
            UiDraw.centeredText(g, "—", ix + previewW / 2f, iy + previewH / 2f - 4, theme.textMuted, false);
        } else {
            g.textureRegion(cape.asset(), ix, iy, previewW, previewH, U0, V0, U1, V1, owned ? 0xFFFFFFFF : 0x60FFFFFF);
        }
        String name = cape == null ? I18n.tr("cosmetics.none", "None") : cape.name();
        g.push();
        g.translate(x + CARD_W / 2f, y + CARD_H - 23);
        g.scale(0.8f, 0.8f);
        UiDraw.centeredText(g, UiDraw.ellipsize(g, name, (int) ((CARD_W - 6) / 0.8f)), 0, 0, owned ? theme.text : theme.textMuted, false);
        String tag = equipped ? I18n.tr("cosmetics.equipped", "Equipped")
                : !owned ? I18n.tr("cosmetics.locked", "Locked")
                : cape != null && cape.seasonal() ? I18n.tr("cosmetics.seasonal", "Seasonal") : "";
        if (!tag.isEmpty()) {
            UiDraw.centeredText(g, tag, 0, 12, equipped ? theme.accent : theme.textMuted, false);
        }
        g.pop();
    }

    private void renderPreview(Gfx g, Theme theme, CosmeticsManager cosmetics) {
        float x = px + pw - 178;
        float w = 164;
        float y = py + 40;
        UiDraw.roundRectBordered(g, x, y, w, ph - 76, 6, theme.surface, theme.border);
        int previewW = 64;
        int previewH = 102;
        int ix = (int) (x + (w - previewW) / 2f);
        int iy = (int) y + 10;
        if (selected == null) {
            UiDraw.roundRect(g, ix, iy, previewW, previewH, 4, Colors.withAlpha(theme.border, 0x80));
        } else {
            g.textureRegion(selected.asset(), ix, iy, previewW, previewH, U0, V0, U1, V1, 0xFFFFFFFF);
        }
        float ty = iy + previewH + 8;
        UiDraw.centeredText(g, selected == null ? I18n.tr("cosmetics.none", "None") : selected.name(), x + w / 2f, ty, theme.text, false);
        ty += 14;
        String line = selected != null && !cosmetics.owns(selected)
                ? I18n.tr("cosmetics.notOwned", "Not available on your account.")
                : visibility(Meridian.get().account());
        g.push();
        g.translate(x + 8, ty);
        g.scale(0.75f, 0.75f);
        int maxWidth = (int) ((w - 16) / 0.75f);
        float ly = 0;
        for (String part : UiDraw.wrap(g, line, maxWidth)) {
            g.text(part, 0, ly, theme.textDim, false);
            ly += 11;
        }
        ly += 4;
        for (String part : UiDraw.wrap(g, I18n.tr("cosmetics.hint", "Shown in third person (F5) and in your inventory."), maxWidth)) {
            g.text(part, 0, ly, theme.textMuted, false);
            ly += 11;
        }
        g.pop();
    }

    /** Who sees the cape, from the state of the Meridian account. */
    static String visibility(MeridianAccountService account) {
        switch (account.state()) {
            case SIGNED_IN:
                return I18n.tr("cosmetics.visible.everyone", "Other Meridian players see your cape.");
            case SIGNING_IN:
                return I18n.tr("cosmetics.visible.signingIn", "Signing in to Meridian services...");
            case OFFLINE_ACCOUNT:
                return I18n.tr("cosmetics.visible.offline", "Only you see it: Meridian services need a Microsoft account.");
            case FAILED:
                return I18n.tr("cosmetics.visible.failed", "Only you see it: signing in to Meridian services failed.");
            default:
                return I18n.tr("cosmetics.visible.local", "Only you see it: Meridian services are not configured.");
        }
    }

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (doneButton.mouseClicked(mx, my, button)) {
            return true;
        }
        List<Cosmetic> capes = capes();
        for (int i = 0; i <= capes.size(); i++) {
            float x = gridX + (i % columns) * (CARD_W + GAP);
            float y = gridY + (i / columns) * (CARD_H + GAP);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
                choose(i == 0 ? null : capes.get(i - 1));
                return true;
            }
        }
        return false;
    }

    private void choose(Cosmetic cape) {
        selected = cape;
        CosmeticsManager cosmetics = Meridian.get().cosmetics();
        if (cape == null) {
            cosmetics.unequip(CosmeticType.CAPE);
        } else {
            cosmetics.equip(cape);
        }
    }
}
