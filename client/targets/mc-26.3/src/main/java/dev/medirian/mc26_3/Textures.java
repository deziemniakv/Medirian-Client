package dev.medirian.mc26_3;

import com.mojang.blaze3d.platform.NativeImage;
import dev.medirian.core.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads Medirian's textures straight from the mod jar and registers them as dynamic textures.
 * This avoids depending on Fabric API's resource loader just for a few GUI images.
 */
final class Textures {

    record Entry(Identifier id, int width, int height) {
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final Entry MISSING = new Entry(null, 0, 0);

    private Textures() {
    }

    static Entry get(Minecraft minecraft, String path) {
        Entry entry = CACHE.get(path);
        if (entry == null) {
            entry = load(minecraft, path);
            CACHE.put(path, entry);
        }
        return entry == MISSING ? null : entry;
    }

    private static Entry load(Minecraft minecraft, String path) {
        String resource = "/assets/medirian/textures/" + path;
        try (InputStream in = Textures.class.getResourceAsStream(resource)) {
            if (in == null) {
                Log.warn("Missing texture {}", resource);
                return MISSING;
            }
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("medirian", "dynamic/" + path.replace('.', '_'));
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "medirian:" + path, image));
            return new Entry(id, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            Log.error("Failed to load texture {}", resource, e);
            return MISSING;
        }
    }
}
