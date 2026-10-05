package dev.medirian.render.font;

import dev.medirian.render.UiDrawTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontTest {

    /** Records uploads and glyph draws (GUI scale 2). */
    static final class RecordingGfx extends UiDrawTest.RasterGfx {
        final Set<String> uploaded = new HashSet<String>();
        final List<int[]> glyphs = new ArrayList<int[]>();
        final List<Integer> colors = new ArrayList<Integer>();

        @Override
        public void uploadTexture(String id, int width, int height, int[] argb) {
            uploaded.add(id);
        }

        @Override
        public void dynamicTexture(String id, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
            assertTrue(uploaded.contains(id), "drawn after the upload");
            glyphs.add(new int[] {x, y, width, height});
            colors.add(argbTint);
        }
    }

    @Test
    void theFontLoadsAndCoversTheLanguagesOfTheClient() {
        MedirianFont font = MedirianFont.load();
        assertNotNull(font);
        assertTrue(font.canDisplay("Zażółć gęślą jaźń · Größe · Añadir ¿qué?"));
        assertFalse(font.canDisplay("5❤"), "symbols outside the font fall back to Minecraft's font");
        assertTrue(font.width('W', false) > font.width('i', false));
        assertTrue(font.width('W', true) >= font.width('W', false));
    }

    @Test
    void textIsDrawnAtTheExactPixelSizeWithShadowAndColours() {
        MedirianFont font = MedirianFont.load();
        RecordingGfx g = new RecordingGfx();
        FontGfx text = new FontGfx(font, g);
        int end = text.text("A§cB C", 10, 20, 0xFFFFFFFF, true);
        // 3 visible glyphs (the space has no pixels), drawn twice (shadow first)
        assertEquals(6, g.glyphs.size());
        assertEquals(FontGfx.shadow(0xFFFFFFFF), (int) g.colors.get(0));
        assertEquals(0xFFFF5555, (int) g.colors.get(4), "§c turns the text red");
        assertEquals(0xFFFFFFFF, (int) g.colors.get(3));
        assertEquals(Math.round(10 + text.width("A§cB C")), end, 1);
        // at GUI scale 2 the glyphs are drawn in real pixels, about two em-sizes tall at most
        for (int[] glyph : g.glyphs) {
            assertTrue(glyph[3] > 0 && glyph[3] <= MedirianFont.EM * 2 + 4, "height " + glyph[3]);
        }
        assertEquals(text.textWidth("Hello"), Math.round(text.width("Hello")));
        assertEquals(9, text.fontHeight());
    }

    @Test
    void unsupportedSymbolsUseTheVersionFont() {
        RecordingGfx g = new RecordingGfx();
        FontGfx text = new FontGfx(MedirianFont.load(), g);
        text.text("♥ 20", 0, 0, 0xFFFFFFFF, false);
        assertTrue(g.glyphs.isEmpty());
        assertEquals(g.textWidth("♥ 20"), text.textWidth("♥ 20"));
    }
}
