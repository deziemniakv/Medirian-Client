package dev.medirian.render;

import dev.medirian.platform.EffectView;
import dev.medirian.platform.EntityView;
import dev.medirian.platform.ItemView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UiDrawTest {

    /** Rasterises fills into a coverage map (GUI scale 2), counting how often each pixel is drawn. */
    public static class RasterGfx implements Gfx {
        final int size = 200;
        final double[] coverage = new double[size * size];
        final int[] draws = new int[size * size];
        private final float[] stack = new float[64 * 3];
        private int depth;
        private float tx;
        private float ty;
        private float scale = 1f;

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            if ((argb >>> 24) == 0 || x2 <= x1 || y2 <= y1) {
                return;
            }
            double a = (argb >>> 24) / 255.0;
            int px1 = (int) Math.round((tx + x1 * scale) * guiScale());
            int py1 = (int) Math.round((ty + y1 * scale) * guiScale());
            int px2 = (int) Math.round((tx + x2 * scale) * guiScale());
            int py2 = (int) Math.round((ty + y2 * scale) * guiScale());
            for (int y = py1; y < py2; y++) {
                for (int x = px1; x < px2; x++) {
                    int i = y * size + x;
                    coverage[i] += a * (1 - coverage[i]);
                    draws[i]++;
                }
            }
        }

        double total() {
            double sum = 0;
            for (double c : coverage) {
                sum += c;
            }
            return sum;
        }

        int maxDraws() {
            int max = 0;
            for (int d : draws) {
                max = Math.max(max, d);
            }
            return max;
        }

        @Override
        public int width() {
            return size / 2;
        }

        @Override
        public int height() {
            return size / 2;
        }

        @Override
        public double guiScale() {
            return 2;
        }

        @Override
        public double pixelScale() {
            return 2 * scale;
        }

        @Override
        public void push() {
            int i = depth++ * 3;
            stack[i] = tx;
            stack[i + 1] = ty;
            stack[i + 2] = scale;
        }

        @Override
        public void pop() {
            int i = --depth * 3;
            tx = stack[i];
            ty = stack[i + 1];
            scale = stack[i + 2];
        }

        @Override
        public void translate(float x, float y) {
            tx += x * scale;
            ty += y * scale;
        }

        @Override
        public void scale(float x, float y) {
            scale *= x;
        }

        @Override
        public void gradient(int x1, int y1, int x2, int y2, int topArgb, int bottomArgb) {
        }

        @Override
        public int text(String text, float x, float y, int argb, boolean shadow) {
            return (int) x;
        }

        @Override
        public int textWidth(String text) {
            return text.length() * 6;
        }

        @Override
        public int fontHeight() {
            return 9;
        }

        @Override
        public void enableScissor(int x1, int y1, int x2, int y2) {
        }

        @Override
        public void disableScissor() {
        }

        @Override
        public void item(ItemView item, int x, int y) {
        }

        @Override
        public void effectIcon(EffectView effect, int x, int y) {
        }

        @Override
        public void entityHead(EntityView entity, int x, int y, int size) {
        }

        @Override
        public void texture(String path, int x, int y, int width, int height, int argbTint) {
        }

        @Override
        public void textureRegion(String path, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        }

        @Override
        public void uploadTexture(String id, int width, int height, int[] argb) {
        }

        @Override
        public void dynamicTexture(String id, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argbTint) {
        }

        @Override
        public void richText(Object nativeText, float x, float y, int argb, boolean shadow) {
        }

        @Override
        public int richTextWidth(Object nativeText) {
            return 0;
        }
    }

    @Test
    void roundRectCoversItsAreaOnceWithSoftEdges() {
        RasterGfx g = new RasterGfx();
        // 40×20 GUI units with a radius of 6 = 80×40 real pixels, radius 12
        UiDraw.roundRect(g, 10, 10, 40, 20, 6, 0xFFFFFFFF);
        double expected = 80 * 40 - (4 - Math.PI) * 12 * 12;
        assertEquals(expected, g.total(), 2.0);
        // edge pixels never overlap the spans (that would darken the outline)
        assertEquals(1, g.maxDraws());
        int partial = 0;
        for (int y = 20; y < 60; y++) {
            for (int x = 20; x < 100; x++) {
                double c = g.coverage[y * g.size + x];
                // mirror-symmetric in both directions
                assertEquals(c, g.coverage[y * g.size + (119 - x)], 1e-9);
                assertEquals(c, g.coverage[(79 - y) * g.size + x], 1e-9);
                if (c > 0 && c < 1) {
                    partial++;
                }
            }
        }
        assertTrue(partial >= 4 * 12, "corners have anti-aliased pixels: " + partial);
    }

    @Test
    void circleHasTheAreaOfADisc() {
        RasterGfx g = new RasterGfx();
        UiDraw.circle(g, 50, 50, 10, 0xFFFFFFFF);
        assertEquals(Math.PI * 20 * 20, g.total(), 3.0);
        assertEquals(1, g.maxDraws());
    }

    @Test
    void coverageRampsAcrossTheEdge() {
        assertEquals(1, UiDraw.coverage(0, 0, 5), 0);
        assertEquals(0.5, UiDraw.coverage(5, 0, 5), 1e-9);
        assertEquals(0, UiDraw.coverage(6, 0, 5), 0);
        assertEquals(0, UiDraw.withCoverage(0x80FFFFFF, 0.001));
        assertEquals(0x40123456, UiDraw.withCoverage(0x80123456, 0.5));
    }
}
