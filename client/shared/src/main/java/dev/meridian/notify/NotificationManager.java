package dev.meridian.notify;

import dev.meridian.render.Anim;
import dev.meridian.render.Colors;
import dev.meridian.render.Gfx;
import dev.meridian.render.Theme;
import dev.meridian.render.UiDraw;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Small, unobtrusive toasts in the top-right corner ("Profile loaded.", "Screenshot saved.").
 *
 * <p>{@link #post} is thread-safe (the launcher bridge posts from its socket thread); toasts are
 * moved to the render list on the render thread. At most {@link #MAX_VISIBLE} are shown at once.
 */
public final class NotificationManager {

    public enum Level { INFO, SUCCESS, WARNING, SEASONAL }

    private static final int MAX_VISIBLE = 4;
    private static final long LIFETIME_MS = 3800;
    private static final int WIDTH = 168;

    private final ConcurrentLinkedQueue<Toast> incoming = new ConcurrentLinkedQueue<Toast>();
    private final List<Toast> visible = new ArrayList<Toast>();
    private volatile boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void post(String title, String message, Level level) {
        if (enabled) {
            incoming.add(new Toast(title, message, level));
        }
    }

    public void post(String title, String message) {
        post(title, message, Level.INFO);
    }

    /** Draws active toasts in the top-right corner of a {@code width}-wide space. */
    public void render(Gfx g, float width) {
        Toast next;
        while ((next = incoming.poll()) != null) {
            visible.add(0, next);
            if (visible.size() > MAX_VISIBLE) {
                visible.remove(visible.size() - 1);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Theme theme = Theme.current();
        float y = 6;
        for (int i = 0; i < visible.size(); i++) {
            Toast toast = visible.get(i);
            if (toast.createdMs == 0) {
                toast.createdMs = now;
            }
            long age = now - toast.createdMs;
            if (age > LIFETIME_MS) {
                visible.remove(i--);
                continue;
            }
            boolean leaving = age > LIFETIME_MS - 300;
            float slide = toast.slide.target(leaving ? 0f : 1f).get();
            if (!Anim.enabled() && leaving) {
                slide = 0;
            }
            int height = toast.message == null ? 20 : 30;
            float x = width - 6 - WIDTH * slide;
            float alpha = Math.max(0f, Math.min(1f, slide));
            UiDraw.shadow(g, x, y, WIDTH, height, 4, 2);
            UiDraw.roundRectBordered(g, x, y, WIDTH, height, 4, Colors.fade(theme.panel, alpha), Colors.fade(theme.border, alpha));
            UiDraw.roundRect(g, x + 4, y + 5, 2, height - 10, 1, Colors.fade(color(toast.level, theme), alpha));
            g.text(UiDraw.ellipsize(g, toast.title, WIDTH - 20), x + 11, y + 6, Colors.fade(theme.text, alpha), false);
            if (toast.message != null) {
                g.text(UiDraw.ellipsize(g, toast.message, WIDTH - 20), x + 11, y + 17, Colors.fade(theme.textDim, alpha), false);
            }
            // progress hairline
            float remaining = 1f - Math.min(1f, age / (float) LIFETIME_MS);
            g.fill((int) (x + 4), (int) (y + height - 1), (int) (x + 4 + (WIDTH - 8) * remaining), (int) (y + height),
                    Colors.fade(theme.accentSoft, alpha));
            y += (height + 4) * Math.max(0.2f, slide);
        }
    }

    private static int color(Level level, Theme theme) {
        switch (level) {
            case SUCCESS: return theme.success;
            case WARNING: return theme.warning;
            case SEASONAL: return theme.seasonal;
            default: return theme.accent;
        }
    }

    private static final class Toast {
        final String title;
        final String message;
        final Level level;
        final Anim slide = new Anim(0f, 14f);
        long createdMs;

        Toast(String title, String message, Level level) {
            this.title = title;
            this.message = message;
            this.level = level;
        }
    }
}
