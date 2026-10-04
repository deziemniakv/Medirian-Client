package dev.meridian.render.font;

import java.util.ArrayList;
import java.util.List;

/**
 * Texture pages that glyph bitmaps are packed into (rows of glyphs, white with alpha). A page
 * becomes dirty when glyphs are added; whoever draws uploads dirty pages first.
 */
public final class GlyphAtlas {

    public static final int SIZE = 512;
    private static final int PADDING = 1;

    /** One texture page. */
    public static final class Page {
        public final String id;
        public final int[] pixels = new int[SIZE * SIZE];
        boolean dirty;
        private int rowX = PADDING;
        private int rowY = PADDING;
        private int rowHeight;

        Page(String id) {
            this.id = id;
        }

        public boolean dirty() {
            return dirty;
        }

        public void uploaded() {
            dirty = false;
        }

        /** Reserves w×h pixels; returns {x, y} or null when the page is full. */
        int[] reserve(int w, int h) {
            if (rowX + w + PADDING > SIZE) {
                rowX = PADDING;
                rowY += rowHeight + PADDING;
                rowHeight = 0;
            }
            if (rowY + h + PADDING > SIZE || w + 2 * PADDING > SIZE) {
                return null;
            }
            int[] spot = {rowX, rowY};
            rowX += w + PADDING;
            rowHeight = Math.max(rowHeight, h);
            return spot;
        }
    }

    private final String prefix;
    private final List<Page> pages = new ArrayList<Page>();

    public GlyphAtlas(String prefix) {
        this.prefix = prefix;
    }

    public List<Page> pages() {
        return pages;
    }

    /**
     * Copies an alpha bitmap (w×h, values 0..255) into a page and returns
     * {page index, x, y}; null when the bitmap cannot fit any page.
     */
    int[] add(int[] alpha, int w, int h) {
        for (int i = 0; i <= pages.size(); i++) {
            if (i == pages.size()) {
                if (pages.size() >= 16) {
                    return null;
                }
                pages.add(new Page(prefix + i));
            }
            Page page = pages.get(i);
            int[] spot = page.reserve(w, h);
            if (spot != null) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        page.pixels[(spot[1] + y) * SIZE + spot[0] + x] = (alpha[y * w + x] << 24) | 0xFFFFFF;
                    }
                }
                page.dirty = true;
                return new int[] {i, spot[0], spot[1]};
            }
        }
        return null;
    }
}
