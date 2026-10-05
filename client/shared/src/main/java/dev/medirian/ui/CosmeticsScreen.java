package dev.medirian.ui;

import dev.medirian.account.MedirianAccountService;
import dev.medirian.core.Medirian;
import dev.medirian.cosmetics.Cosmetic;
import dev.medirian.cosmetics.CosmeticType;
import dev.medirian.cosmetics.CosmeticsManager;
import dev.medirian.i18n.I18n;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.ui.widget.Button;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics: one tab per kind this version renders (capes, hats, wings, trails). Clicking an owned
 * cosmetic equips it; the preview shows the selected one.
 */
public final class CosmeticsScreen extends MedirianScreen {

    /** Front of the cape in the 64×32 cape layout (fractions of the texture). */
    private static final float U0 = 1 / 64f;
    private static final float V0 = 1 / 32f;
    private static final float U1 = 11 / 64f;
    private static final float V1 = 17 / 32f;
    private static final int CARD_W = 62;
    private static final int CARD_H = 92;
    private static final int GAP = 8;
    private static final CosmeticType[] ORDER = {CosmeticType.CAPE, CosmeticType.HAT, CosmeticType.WINGS, CosmeticType.TRAIL, CosmeticType.EMOTE};

    private static CosmeticType lastTab = CosmeticType.CAPE;

    private float px;
    private float py;
    private float pw;
    private float ph;
    private float gridX;
    private float gridY;
    private int columns;
    private Button doneButton;
    private final List<CosmeticType> tabs = new ArrayList<CosmeticType>();
    private CosmeticType tab;
    /** Selected cosmetic per tab (null = "None"). */
    private final Map<CosmeticType, Cosmetic> selected = new EnumMap<CosmeticType, Cosmetic>(CosmeticType.class);
    /** Tab bounds from the last frame (x, width), for clicks. */
    private final float[] tabBounds = new float[ORDER.length * 2];

    public CosmeticsScreen(MedirianScreen parent) {
        super(parent);
        CosmeticsManager cosmetics = Medirian.get().cosmetics();
        for (CosmeticType type : ORDER) {
            if (cosmetics.canRender(type) && !cosmetics.catalogue(type).isEmpty()) {
                tabs.add(type);
                selected.put(type, cosmetics.equipped(type));
            }
        }
        tab = tabs.contains(lastTab) ? lastTab : tabs.isEmpty() ? CosmeticType.CAPE : tabs.get(0);
    }

    @Override
    protected void init() {
        pw = Math.min(560, width - 32);
        ph = Math.min(340, height - 32);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        gridX = px + 14;
        gridY = py + 50;
        columns = Math.max(1, (int) ((pw - 190 - 14) / (CARD_W + GAP)));
        doneButton = new Button(parent != null ? I18n.tr("ui.back", "Back") : I18n.tr("ui.done", "Done"),
                Button.Style.SECONDARY, this::close).bounds(px + pw - 164, py + ph - 26, 150, 16);
    }

    private List<Cosmetic> items() {
        return Medirian.get().cosmetics().catalogue(tab);
    }

    static String tabName(CosmeticType type) {
        switch (type) {
            case HAT:
                return I18n.tr("cosmetics.hats", "Hats");
            case WINGS:
                return I18n.tr("cosmetics.wings", "Wings");
            case TRAIL:
                return I18n.tr("cosmetics.trails", "Trails");
            case EMOTE:
                return I18n.tr("cosmetics.emotes", "Emotes");
            default:
                return I18n.tr("cosmetics.capes", "Capes");
        }
    }

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        Theme theme = Theme.current();
        CosmeticsManager cosmetics = Medirian.get().cosmetics();
        renderBackdrop(g);
        UiDraw.shadow(g, px, py, pw, ph, 8, 4);
        UiDraw.roundRectBordered(g, px, py, pw, ph, 8, theme.panel, theme.border);
        g.text("§l" + I18n.tr("cosmetics.title", "Cosmetics"), px + 14, py + 13, theme.text, false);
        renderTabs(g, theme, mx, my);

        List<Cosmetic> items = items();
        Cosmetic equipped = cosmetics.equipped(tab);
        for (int i = 0; i <= items.size(); i++) {
            Cosmetic item = i == 0 ? null : items.get(i - 1);
            float x = gridX + (i % columns) * (CARD_W + GAP);
            float y = gridY + (i / columns) * (CARD_H + GAP);
            renderCard(g, theme, cosmetics, item, item == equipped, x, y, mx, my);
        }

        renderPreview(g, theme, cosmetics);
        doneButton.render(g, mx, my);
    }

    private void renderTabs(Gfx g, Theme theme, float mx, float my) {
        float x = px + 14;
        float y = py + 28;
        for (int i = 0; i < tabs.size(); i++) {
            CosmeticType type = tabs.get(i);
            String name = tabName(type);
            float w = g.textWidth(name) + 14;
            tabBounds[i * 2] = x;
            tabBounds[i * 2 + 1] = w;
            boolean active = type == tab;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
            if (active || hover) {
                UiDraw.roundRect(g, x, y, w, 14, 4, active ? theme.accentSoft : theme.surface);
            }
            g.text(name, x + 7, y + 3, active ? theme.text : theme.textDim, false);
            x += w + 4;
        }
    }

    private CosmeticType tabAt(float mx, float my) {
        float y = py + 28;
        for (int i = 0; i < tabs.size(); i++) {
            float x = tabBounds[i * 2];
            float w = tabBounds[i * 2 + 1];
            if (w > 0 && mx >= x && mx < x + w && my >= y && my < y + 14) {
                return tabs.get(i);
            }
        }
        return null;
    }

    /** Draws a cosmetic's preview into the box: the front of a cape, otherwise its icon. */
    private static void preview(Gfx g, Cosmetic item, int x, int y, int w, int h, int tint) {
        if (item.type() == CosmeticType.CAPE) {
            g.textureRegion(item.asset(), x, y, w, h, U0, V0, U1, V1, tint);
        } else {
            int size = Math.min(w, h);
            g.texture("cosmetics/icons/" + item.id() + ".png", x + (w - size) / 2, y + (h - size) / 2, size, size, tint);
        }
    }

    private void renderCard(Gfx g, Theme theme, CosmeticsManager cosmetics, Cosmetic item, boolean equipped,
                            float x, float y, float mx, float my) {
        boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
        boolean owned = item == null || cosmetics.owns(item);
        boolean isSelected = item == selected.get(tab);
        int border = equipped ? theme.accent : isSelected ? theme.borderStrong : theme.border;
        UiDraw.roundRectBordered(g, x, y, CARD_W, CARD_H, 5, hover ? theme.elevated : theme.surface, border);
        int previewW = 36;
        int previewH = 58;
        int ix = (int) (x + (CARD_W - previewW) / 2f);
        int iy = (int) y + 7;
        if (item == null) {
            UiDraw.roundRect(g, ix, iy, previewW, previewH, 3, Colors.withAlpha(theme.border, 0x80));
            UiDraw.centeredText(g, "—", ix + previewW / 2f, iy + previewH / 2f - 4, theme.textMuted, false);
        } else {
            preview(g, item, ix, iy, previewW, previewH, owned ? 0xFFFFFFFF : 0x60FFFFFF);
        }
        String name = item == null ? I18n.tr("cosmetics.none", "None") : item.name();
        g.push();
        g.translate(x + CARD_W / 2f, y + CARD_H - 23);
        g.scale(0.8f, 0.8f);
        UiDraw.centeredText(g, UiDraw.ellipsize(g, name, (int) ((CARD_W - 6) / 0.8f)), 0, 0, owned ? theme.text : theme.textMuted, false);
        String tag = equipped ? I18n.tr("cosmetics.equipped", "Equipped")
                : !owned ? I18n.tr("cosmetics.locked", "Locked")
                : item != null && item.seasonal() ? I18n.tr("cosmetics.seasonal", "Seasonal") : "";
        if (!tag.isEmpty()) {
            UiDraw.centeredText(g, tag, 0, 12, equipped ? theme.accent : theme.textMuted, false);
        }
        g.pop();
    }

    private void renderPreview(Gfx g, Theme theme, CosmeticsManager cosmetics) {
        float x = px + pw - 178;
        float w = 164;
        float y = py + 50;
        UiDraw.roundRectBordered(g, x, y, w, ph - 86, 6, theme.surface, theme.border);
        Cosmetic item = selected.get(tab);
        int previewW = tab == CosmeticType.CAPE ? 64 : 96;
        int previewH = tab == CosmeticType.CAPE ? 102 : 96;
        int ix = (int) (x + (w - previewW) / 2f);
        int iy = (int) y + 10;
        if (item == null) {
            UiDraw.roundRect(g, ix, iy, previewW, previewH, 4, Colors.withAlpha(theme.border, 0x80));
        } else {
            preview(g, item, ix, iy, previewW, previewH, 0xFFFFFFFF);
        }
        float ty = iy + previewH + 8;
        UiDraw.centeredText(g, item == null ? I18n.tr("cosmetics.none", "None") : item.name(), x + w / 2f, ty, theme.text, false);
        ty += 14;
        String line = item != null && !cosmetics.owns(item)
                ? I18n.tr("cosmetics.notOwned", "Not available on your account.")
                : visibility(Medirian.get().account());
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
        String hint = tab == CosmeticType.TRAIL
                ? I18n.tr("cosmetics.hint.trail", "Particles follow you while you move.")
                : tab == CosmeticType.EMOTE
                ? I18n.tr("cosmetics.hint.emote", "Press {0} to play the chosen emote.", Medirian.get().settings().emoteKey.key().label())
                : I18n.tr("cosmetics.hint", "Shown in third person (F5) and in your inventory.");
        for (String part : UiDraw.wrap(g, hint, maxWidth)) {
            g.text(part, 0, ly, theme.textMuted, false);
            ly += 11;
        }
        g.pop();
    }

    /** Who sees the cosmetics, from the state of the Medirian account. */
    static String visibility(MedirianAccountService account) {
        switch (account.state()) {
            case SIGNED_IN:
                return I18n.tr("cosmetics.visible.everyone", "Other Medirian players see your cape.");
            case SIGNING_IN:
                return I18n.tr("cosmetics.visible.signingIn", "Signing in to Medirian services...");
            case OFFLINE_ACCOUNT:
                return I18n.tr("cosmetics.visible.offline", "Only you see it: Medirian services need a Microsoft account.");
            case FAILED:
                return I18n.tr("cosmetics.visible.failed", "Only you see it: signing in to Medirian services failed.");
            default:
                return I18n.tr("cosmetics.visible.local", "Only you see it: Medirian services are not configured.");
        }
    }

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (doneButton.mouseClicked(mx, my, button)) {
            return true;
        }
        CosmeticType clicked = tabAt(mx, my);
        if (clicked != null) {
            tab = clicked;
            lastTab = clicked;
            return true;
        }
        List<Cosmetic> items = items();
        for (int i = 0; i <= items.size(); i++) {
            float x = gridX + (i % columns) * (CARD_W + GAP);
            float y = gridY + (i / columns) * (CARD_H + GAP);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
                choose(i == 0 ? null : items.get(i - 1));
                return true;
            }
        }
        return false;
    }

    /** Opens the screen on a tab (self-test screenshots). */
    public CosmeticsScreen showTab(CosmeticType type) {
        if (tabs.contains(type)) {
            tab = type;
        }
        return this;
    }

    private void choose(Cosmetic item) {
        selected.put(tab, item);
        CosmeticsManager cosmetics = Medirian.get().cosmetics();
        if (item == null) {
            cosmetics.unequip(tab);
        } else {
            cosmetics.equip(item);
        }
    }
}
