package dev.medirian.ui;

import dev.medirian.account.PlayerIdentity;
import dev.medirian.account.PlayerSkins;
import dev.medirian.core.BuildInfo;
import dev.medirian.core.Medirian;
import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.platform.ClientActions;
import dev.medirian.render.Anim;
import dev.medirian.render.Colors;
import dev.medirian.render.Gfx;
import dev.medirian.render.Pixel;
import dev.medirian.render.Theme;
import dev.medirian.render.UiDraw;
import dev.medirian.ui.widget.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * Medirian's main menu, shown instead of Minecraft's title screen: a cozy Halloween night (the
 * same pixel-art scene as the launcher), the Medirian Client logo, the menu and the player — their
 * real skin, large, standing on the path in front of the cabin with their name above them.
 *
 * <p>The scene is drawn at a whole number of real pixels per art pixel whenever that covers the
 * window closely, so the art stays crisp. Fog drifts, lantern and pumpkin light flickers, the
 * layers move a little with the mouse and now and then a bat crosses the sky; all of it stops
 * when animations are off.
 */
public final class TitleMenuScreen extends MedirianScreen {

    private static final int SCENE_W = 480;
    private static final int SCENE_H = 270;
    private static final int LOGO_W = 134;
    private static final int LOGO_H = 34;
    private static final int FOG_H = 43;
    private static final int FOG_Y = 201;
    private static final int BUTTON_H = 24;
    private static final int BUTTON_GAP = 5;

    private final List<Item> items = new ArrayList<Item>();
    private final Anim intro = new Anim(0f, 5f);
    private final long createdNanos = System.nanoTime();
    private Button cosmeticsButton;
    private int menuX;
    private int menuY;
    private int menuW;
    private int focusIndex = -1;
    private String toast;
    /** Version of the skin texture uploaded to the GPU (-1 = none yet). */
    private int skinVersion = -1;
    /** The scene as drawn this frame: its origin, units per art pixel and parallax offset of the ground. */
    private int sceneX;
    private int sceneY;
    private float sceneUnits = 1f;
    private float groundDx;
    private float groundDy;
    /** Where the player was drawn (for clicks). */
    private int playerX;
    private int playerY;
    private int playerW;
    private int playerH;
    private final Anim playerHover = new Anim(0f, 14f);
    private long toastUntil;

    public TitleMenuScreen() {
        super(null);
    }

    /** One menu entry. */
    private static final class Item {
        final String icon;
        final String label;
        final Runnable action;
        final boolean half;
        int x;
        int y;
        int w;
        final Anim hover = new Anim(0f, 18f);

        Item(String icon, String label, Runnable action, boolean half) {
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.half = half;
        }

        boolean contains(float mx, float my) {
            return mx >= x && my >= y && mx < x + w && my < y + BUTTON_H;
        }
    }

    @Override
    protected void init() {
        final Medirian medirian = Medirian.get();
        final ClientActions actions = medirian.platform().actions();
        items.clear();
        items.add(new Item("singleplayer", I18n.tr("title.singleplayer", "Singleplayer"), actions::openSingleplayer, false));
        items.add(new Item("multiplayer", I18n.tr("title.multiplayer", "Multiplayer"), () -> {
            if (!actions.openMultiplayer()) {
                showToast(I18n.tr("title.multiplayerDisabled", "Multiplayer is disabled for this account."));
            }
        }, false));
        items.add(new Item("mods", I18n.tr("title.mods", "Medirian Mods"), () -> medirian.platform().openScreen(new ModMenuScreen(this)), false));
        items.add(new Item("options", I18n.tr("title.options", "Options"), actions::openVanillaSettings, false));
        items.add(new Item("language", I18n.tr("title.language", "Language"), actions::openLanguageSettings, true));
        items.add(new Item("quit", I18n.tr("title.quit", "Quit"), actions::quitGame, true));

        menuW = 196;
        menuX = Math.max(16, Math.round(width * 0.07f));
        int logoH = logoScaleUnits() * LOGO_H;
        int fullRows = 5;
        int menuH = fullRows * (BUTTON_H + BUTTON_GAP) - BUTTON_GAP;
        int top = Math.max(12, (height - (logoH + 18 + menuH)) / 2 - 10);
        menuY = top + logoH + 18;
        int row = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.half) {
                int half = (menuW - BUTTON_GAP) / 2;
                item.x = menuX + (i == items.size() - 1 ? half + BUTTON_GAP : 0);
                item.w = half;
                item.y = menuY + row * (BUTTON_H + BUTTON_GAP);
                if (i == items.size() - 1) {
                    row++;
                }
            } else {
                item.x = menuX;
                item.w = menuW;
                item.y = menuY + row * (BUTTON_H + BUTTON_GAP);
                row++;
            }
        }
        cosmeticsButton = medirian.cosmetics().available()
                ? new Button(I18n.tr("ui.cosmetics", "Cosmetics"), Button.Style.SECONDARY,
                        () -> medirian.platform().openScreen(new CosmeticsScreen(this))).icon("cosmetics")
                : null;
    }

    /** Medirian units per logo pixel: the logo takes about a third of the width. */
    private int logoScaleUnits() {
        return Math.max(1, Math.min(3, Math.round(width * 0.3f / LOGO_W)));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void render(Gfx g, float mx, float my, float delta) {
        Theme t = Theme.current();
        double seconds = Anim.enabled() ? (System.nanoTime() - createdNanos) / 1e9 : 0;
        renderScene(g, mx, my, seconds);
        if (Theme.snowing()) {
            renderBackdropSnow(g);
        }
        float appear = intro.target(1f).get();
        renderLogo(g, t, appear);
        renderPlayer(g, t, mx, my, appear);
        renderMenu(g, t, mx, my, appear);
        if (cosmeticsButton != null) {
            cosmeticsButton.bounds(width - 108, 8, 100, 18);
            cosmeticsButton.render(g, mx, my);
        }
        renderFooter(g, t);
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int w = g.textWidth(toast) + 12;
            int x = (width - w) / 2;
            Pixel.panel(g, x, height - 52, w, 18);
            g.text(toast, x + 6, height - 47, t.text, false);
        }
    }

    private void renderScene(Gfx g, float mx, float my, double seconds) {
        double ppu = g.pixelScale();
        double cover = Math.max(width * ppu / SCENE_W, height * ppu / SCENE_H);
        // whole real pixels per art pixel when that is close enough to covering the window
        double scale = Math.ceil(cover - 0.001);
        if (scale / cover > 1.18) {
            scale = cover;
        }
        float unitsPerPixel = (float) (scale / ppu);
        int sw = Math.round(SCENE_W * unitsPerPixel);
        int sh = Math.round(SCENE_H * unitsPerPixel);
        int x0 = (width - sw) / 2;
        int y0 = height - sh;
        float px = Anim.enabled() ? (mx / Math.max(1, width) - 0.5f) : 0f;
        float py = Anim.enabled() ? (my / Math.max(1, height) - 0.5f) : 0f;
        g.fill(0, 0, width, height, 0xFF0C0814);
        layer(g, "sky", x0, y0, sw, sh, px * -2, py * -1, 0xFFFFFFFF);
        layer(g, "far", x0, y0, sw, sh, px * -4, py * -2, 0xFFFFFFFF);
        renderBats(g, x0, y0, unitsPerPixel, seconds);
        layer(g, "forest", x0, y0, sw, sh, px * -6, py * -3, 0xFFFFFFFF);
        // fog drifts right to left, two copies side by side
        int fogY = Math.round(y0 + FOG_Y * unitsPerPixel + py * -4);
        int fogH = Math.round(FOG_H * unitsPerPixel);
        int drift = (int) Math.round((seconds * 6 * unitsPerPixel) % sw);
        g.texture("gui/scene/fog.png", x0 - drift, fogY, sw, fogH, 0xFFFFFFFF);
        g.texture("gui/scene/fog.png", x0 - drift + sw, fogY, sw, fogH, 0xFFFFFFFF);
        layer(g, "ground", x0, y0, sw, sh, px * -9, py * -4, 0xFFFFFFFF);
        sceneX = x0;
        sceneY = y0;
        sceneUnits = unitsPerPixel;
        groundDx = px * -9;
        groundDy = py * -4;
        // lantern and pumpkin light flickers gently
        float flicker = (float) (0.86 + 0.08 * Math.sin(seconds * 3.1) + 0.06 * Math.sin(seconds * 7.3 + 1.2));
        layer(g, "glow", x0, y0, sw, sh, px * -9, py * -4, Colors.withAlpha(0xFFFFFF, Math.round(255 * Math.min(1f, flicker))));
    }

    private static void layer(Gfx g, String name, int x0, int y0, int sw, int sh, float dx, float dy, int tint) {
        g.texture("gui/scene/" + name + ".png", Math.round(x0 + dx), Math.round(y0 + dy), sw, sh, tint);
    }

    /** Every 16 s a bat flutters across the upper sky. */
    private void renderBats(Gfx g, int x0, int y0, float unitsPerPixel, double seconds) {
        if (seconds <= 0) {
            return;
        }
        double cycle = 16.0;
        double phase = (seconds + 4) % cycle / 7.0; // crossing takes 7 s, then a pause
        if (phase > 1) {
            return;
        }
        int n = (int) ((seconds + 4) / cycle);
        float y = (float) (y0 + (40 + (n * 37) % 50) * unitsPerPixel + Math.sin(seconds * 5) * 3 * unitsPerPixel);
        float x = (float) (x0 + (SCENE_W + 20 - phase * (SCENE_W + 40)) * unitsPerPixel);
        boolean up = ((int) (seconds * 8)) % 2 == 0;
        int w = Math.round(11 * unitsPerPixel);
        int h = Math.round((up ? 5 : 3) * unitsPerPixel);
        g.texture("gui/bat.png", Math.round(x), Math.round(y), w, h, 0xFFFFFFFF);
    }

    private void renderBackdropSnow(Gfx g) {
        double seconds = Anim.enabled() ? System.nanoTime() / 1e9 : 0;
        for (int i = 0; i < 80; i++) {
            double r1 = Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1;
            double r2 = Math.abs(Math.sin(i * 78.233) * 43758.5453) % 1;
            double speed = 6 + r2 * 10;
            int y = (int) ((seconds * speed + r1 * (height + 10)) % (height + 10)) - 5;
            int x = (int) (r1 * width + Math.sin(seconds * 0.6 + i) * 6);
            int size = r2 > 0.7 ? 2 : 1;
            g.fill(x, y, x + size, y + size, 0x90EAF4FB);
        }
    }

    private void renderLogo(Gfx g, Theme t, float appear) {
        int s = logoScaleUnits();
        int y = menuY - 18 - LOGO_H * s - Math.round((1f - appear) * 6);
        g.texture("gui/logo.png", menuX - 2 * s, y, LOGO_W * s, LOGO_H * s, Colors.withAlpha(0xFFFFFF, Math.round(255 * appear)));
    }

    private void renderMenu(Gfx g, Theme t, float mx, float my, float appear) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            float delay = Math.max(0f, Math.min(1f, appear * 1.6f - i * 0.1f));
            int slide = Math.round((1f - delay) * -14);
            boolean hovered = item.contains(mx, my);
            if (hovered) {
                focusIndex = -1;
            }
            boolean hot = hovered || i == focusIndex;
            float h = item.hover.target(hot ? 1f : 0f).get();
            int x = item.x + slide + Math.round(h * 2);
            int y = item.y;
            int fill = hot ? t.surfaceLight : t.panel;
            Pixel.frame(g, x, y, item.w, BUTTON_H, fill, hot ? 0xFF4C3870 : t.panelLight, t.panelDark);
            // the icon sits in a dark slot, like an item in an inventory
            Pixel.inset(g, x + 3, y + 3, 18, 18, hot ? t.surfaceDark : t.inset);
            Pixel.icon(g, item.icon, x + 4, y + 4, 1, 0xFFFFFFFF);
            String label = UiDraw.ellipsize(g, item.label, item.w - 32);
            g.text(label, x + 27, y + 8, hot ? t.pumpkinLight : t.text, false);
            if (hot) {
                Pixel.highlight(g, x, y, item.w, BUTTON_H, t.pumpkin);
            }
        }
    }

    /**
     * The player as the launcher shows them, large: their real skin (front, with the outer layer)
     * standing on the path of the scene, about 40% of the screen tall, with a soft shadow at their
     * feet and their name on a tag above their head. Clicking them opens the cosmetics.
     */
    private void renderPlayer(Gfx g, Theme t, float mx, float my, float appear) {
        Medirian medirian = Medirian.get();
        PlayerIdentity identity = medirian.platform().identity();
        PlayerSkins.Skin skin = PlayerSkins.get(medirian.home().root(), identity);
        if (skin.version != skinVersion) {
            g.uploadTexture("player_skin", 64, 64, skin.argb);
            skinVersion = skin.version;
        }
        // whole units per skin pixel keep the skin crisp; at least 3, about 40% of the height
        int s = Math.max(3, (int) Math.floor(height * 0.4f / 32f));
        int figureW = 16 * s;
        int figureH = 32 * s;
        // the path in front of the cabin: right of the menu, on the ground line of the scene
        int menuRight = menuX + menuW;
        int centre = Math.round(Math.max(menuRight + figureW * 0.75f, width * 0.62f) + groundDx);
        int feet = Math.round(sceneY + 237 * sceneUnits + groundDy);
        feet = Math.min(feet, height - 18);
        int x = centre - figureW / 2;
        int y = feet - figureH - Math.round((1f - appear) * 8);
        playerX = x;
        playerY = y;
        playerW = figureW;
        playerH = figureH;
        boolean hovered = cosmeticsButton != null && mx >= x && mx < x + figureW && my >= y && my < y + figureH;
        float h = playerHover.target(hovered ? 1f : 0f).get();
        // a soft shadow on the ground
        UiDraw.roundRect(g, centre - figureW * 0.55f, feet - s * 0.6f, figureW * 1.1f, s * 1.4f, s * 0.7f, 0x55000000);
        figure(g, x, y, s, skin.slim);
        // the name tag, as Minecraft draws it above players
        String name = UiDraw.ellipsize(g, identity.name(), Math.max(60, figureW * 2));
        int tagW = g.textWidth(name) + 8;
        int tagX = centre - tagW / 2;
        int tagY = y - 16;
        g.fill(tagX, tagY, tagX + tagW, tagY + 12, Colors.lerp(0x66000000, 0x88000000, h));
        g.text(name, tagX + 4, tagY + 2, Colors.lerp(t.text, t.pumpkinLight, h), false);
    }

    /** The front of a skin: head, body, arms and legs with their outer layers, {@code s} units per pixel. */
    private static void figure(Gfx g, int x, int y, int s, boolean slim) {
        int arm = slim ? 3 : 4;
        int[][] parts = {
            // {u, v, width, height, x, y} in skin pixels; inner layers first, then the outer ones
            {8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8}, {44, 20, arm, 12, 4 - arm, 8}, {36, 52, arm, 12, 12, 8},
            {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20},
            {40, 8, 8, 8, 4, 0}, {20, 36, 8, 12, 4, 8}, {44, 36, arm, 12, 4 - arm, 8}, {52, 52, arm, 12, 12, 8},
            {4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20}
        };
        for (int[] p : parts) {
            g.dynamicTexture("player_skin", x + p[4] * s, y + p[5] * s, p[2] * s, p[3] * s,
                    p[0] / 64f, p[1] / 64f, (p[0] + p[2]) / 64f, (p[1] + p[3]) / 64f, 0xFFFFFFFF);
        }
    }

    private void renderFooter(Gfx g, Theme t) {
        Medirian medirian = Medirian.get();
        String left = "Medirian Client " + BuildInfo.VERSION + " · Minecraft " + medirian.platform().minecraftVersion();
        Pixel.text(g, left, 6, height - 12, t.textDim);
        String right = I18n.tr("title.disclaimer", "Not an official Minecraft product.");
        int rw = g.textWidth(right);
        if (6 + g.textWidth(left) + 16 + rw < width) {
            Pixel.text(g, right, width - 6 - rw, height - 12, t.textMuted);
        }
    }

    private void showToast(String message) {
        toast = message;
        toastUntil = System.currentTimeMillis() + 3500;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(float mx, float my, int button) {
        if (button != 0) {
            return false;
        }
        if (cosmeticsButton != null && cosmeticsButton.mouseClicked(mx, my, button)) {
            return true;
        }
        // the player opens their cosmetics, like a wardrobe
        if (cosmeticsButton != null && mx >= playerX && mx < playerX + playerW && my >= playerY && my < playerY + playerH) {
            Medirian.get().platform().openScreen(new CosmeticsScreen(this));
            return true;
        }
        for (Item item : items) {
            if (item.contains(mx, my)) {
                item.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean keyPressed(Key key, boolean ctrl, boolean shift) {
        if (key == Key.DOWN || key == Key.TAB) {
            focusIndex = (focusIndex + 1) % items.size();
            return true;
        }
        if (key == Key.UP) {
            focusIndex = focusIndex <= 0 ? items.size() - 1 : focusIndex - 1;
            return true;
        }
        if ((key == Key.ENTER || key == Key.SPACE) && focusIndex >= 0) {
            items.get(focusIndex).action.run();
            return true;
        }
        if (key == Medirian.get().settings().modMenuKey.key()) {
            Medirian.get().platform().openScreen(new ModMenuScreen(this));
            return true;
        }
        return key == Key.ESCAPE;
    }

    /** The main menu stays open: there is nothing behind it. */
    @Override
    public void close() {
    }
}
