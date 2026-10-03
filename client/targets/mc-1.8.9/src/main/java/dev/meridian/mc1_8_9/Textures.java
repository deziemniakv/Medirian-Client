package dev.meridian.mc1_8_9;

import dev.meridian.core.Log;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads Meridian textures from the mod jar as dynamic textures (Legacy Fabric does not expose mod
 * jars as resource packs without Legacy Fabric API).
 */
final class Textures {

    static final class Entry {
        final Identifier id;
        final int width;
        final int height;

        Entry(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();
    private static final Entry MISSING = new Entry(null, 0, 0);

    private Textures() {
    }

    static Entry get(MinecraftClient client, String path) {
        Entry entry = CACHE.get(path);
        if (entry == null) {
            entry = load(client, path);
            CACHE.put(path, entry);
        }
        return entry == MISSING ? null : entry;
    }

    private static Entry load(MinecraftClient client, String path) {
        String resource = "/assets/meridian/textures/" + path;
        InputStream in = Textures.class.getResourceAsStream(resource);
        if (in == null) {
            Log.warn("Missing texture {}", resource);
            return MISSING;
        }
        try {
            BufferedImage image = ImageIO.read(in);
            Identifier id = client.getTextureManager().registerDynamicTexture("meridian_" + path.replace('/', '_'),
                    new NativeImageBackedTexture(image));
            return new Entry(id, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            Log.error("Failed to load texture {}", resource, e);
            return MISSING;
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                // closing anyway
            }
        }
    }
}
