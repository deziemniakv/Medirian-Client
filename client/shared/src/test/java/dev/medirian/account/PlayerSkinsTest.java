package dev.medirian.account;

import com.google.gson.JsonParser;
import dev.medirian.cosmetics.model.CosmeticModels;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkinsTest {

    @Test
    void legacySkinsGetMirroredLeftLimbsLikeMinecraft() {
        int[] legacy = new int[64 * 32];
        // right leg front (4..8, 20..32): a gradient so the mirror is visible
        for (int y = 20; y < 32; y++) {
            for (int x = 4; x < 8; x++) {
                legacy[y * 64 + x] = 0xFF000000 | (x << 16) | y;
            }
        }
        int[] px = PlayerSkins.normalize(legacy, 32);
        assertEquals(64 * 64, px.length);
        // left leg front is (20..24, 52..64), mirrored horizontally
        for (int y = 0; y < 12; y++) {
            for (int i = 0; i < 4; i++) {
                assertEquals(legacy[(20 + y) * 64 + 4 + i], px[(52 + y) * 64 + 20 + (3 - i)]);
            }
        }
    }

    @Test
    void anOpaqueLegacyHatLayerIsDroppedAndBaseLayersBecomeOpaque() {
        int[] legacy = new int[64 * 32];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                legacy[y * 64 + x] = 0xFF336699;
            }
        }
        legacy[8 * 64 + 8] = 0x00112233; // a transparent pixel on the face
        int[] px = PlayerSkins.normalize(legacy, 32);
        // Notch's transparency hack: no transparent pixel in the hat layer -> the layer is hidden
        assertEquals(0xFF, px[0] >>> 24, "base pixel opaque");
        assertEquals(0, px[4 * 64 + 40] >>> 24);
        // the face is always opaque
        assertEquals(0xFF112233, px[8 * 64 + 8]);

        int[] modern = new int[64 * 64];
        modern[4 * 64 + 40] = 0x00FFFFFF;
        modern[5 * 64 + 41] = 0xFFAA00AA;
        int[] kept = PlayerSkins.normalize(modern, 64);
        assertEquals(0xFFAA00AA, kept[5 * 64 + 41], "64x64 skins keep their hat layer");
    }

    @Test
    void readsTheSkinUrlAndModelFromASessionProfile() {
        String textures = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\",\"metadata\":{\"model\":\"slim\"}}}}";
        String profile = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":[{\"name\":\"textures\",\"value\":\""
                + Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8)) + "\"}]}";
        assertArrayEquals(new String[] {"https://textures.minecraft.net/texture/abc", "slim"},
                PlayerSkins.textures(new JsonParser().parse(profile).getAsJsonObject()));
        assertNull(PlayerSkins.textures(new JsonParser().parse("{\"properties\":[]}").getAsJsonObject()));
    }

    @Test
    void hatBoxesMustWrapTheHeadOrStayOutsideIt() {
        // a brim around the head and a crown that wraps the hat layer
        assertTrue(CosmeticModels.fits(-6, -6.5f, -6, 6, -5.5f, 6));
        assertTrue(CosmeticModels.fits(-4.6f, -15.5f, -4.6f, 4.6f, -6.5f, 4.6f));
        // a box above the hat layer or in front of the face
        assertTrue(CosmeticModels.fits(-2, -12, -2, 2, -9, 2));
        assertTrue(CosmeticModels.fits(-1, -8, -5.1f, 1, -7, -4.6f));
        // a crown exactly as wide as the head would show the skin's hat layer through it
        assertFalse(CosmeticModels.fits(-4, -10, -4, 4, -6, 4));
        assertFalse(CosmeticModels.fits(-4.5f, -10, -4.5f, 4.5f, -6, 4.5f));
    }
}
