package dev.medirian.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.medirian.core.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * The signed-in player's Minecraft skin for Medirian's own screens (the main menu shows the same
 * skin as the launcher). Sources, in order: the launcher's cache (MEDIRIAN_HOME/cache/skins/<uuid>),
 * Mojang's session server (fetched in the background and cached), Medirian's default skin.
 * Skins are prepared like Minecraft prepares them (see {@link #normalize}).
 */
public final class PlayerSkins {

    /** A 64×64 ARGB skin and its arm model. */
    public static final class Skin {
        public final int[] argb;
        public final boolean slim;
        /** false for Medirian's default skin. */
        public final boolean real;
        public final int version;

        Skin(int[] argb, boolean slim, boolean real, int version) {
            this.argb = argb;
            this.slim = slim;
            this.real = real;
            this.version = version;
        }
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Object LOCK = new Object();
    private static volatile Skin current;
    private static volatile String loadedFor;
    private static int versions;

    private PlayerSkins() {
    }

    /** The skin of {@code identity}; the default skin until the real one has loaded. */
    public static Skin get(File home, PlayerIdentity identity) {
        String uuid = identity.uuid() == null ? "" : identity.uuid().replace("-", "");
        if (!uuid.equals(loadedFor)) {
            synchronized (LOCK) {
                if (!uuid.equals(loadedFor)) {
                    loadedFor = uuid;
                    current = defaultSkin();
                    load(home, identity, uuid);
                }
            }
        }
        return current;
    }

    private static void load(final File home, final PlayerIdentity identity, final String uuid) {
        final File dir = new File(new File(home, "cache"), "skins");
        final File png = new File(dir, uuid + ".png");
        final File meta = new File(dir, uuid + ".json");
        if (png.isFile()) {
            Skin cached = read(png, slim(meta));
            if (cached != null) {
                current = cached;
                return;
            }
        }
        if (!identity.online() || uuid.isEmpty()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                JsonObject profile = json("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                if (profile == null) {
                    return;
                }
                String[] skin = textures(profile);
                if (skin == null) {
                    return;
                }
                byte[] bytes = download(skin[0]);
                Skin loaded = decode(bytes, "slim".equals(skin[1]));
                if (loaded != null && uuid.equals(loadedFor)) {
                    current = loaded;
                    dir.mkdirs();
                    try (OutputStream out = new FileOutputStream(png)) {
                        out.write(bytes);
                    }
                    try (OutputStream out = new FileOutputStream(meta)) {
                        out.write(("{\"url\":\"" + skin[0] + "\",\"model\":\"" + skin[1] + "\",\"name\":\"" + identity.name()
                                + "\",\"fetchedAt\":" + System.currentTimeMillis() + "}").getBytes(UTF_8));
                    }
                }
            } catch (Exception e) {
                Log.warn("Could not load the skin of {}: {}", identity.name(), e.toString());
            }
        }, "Medirian skin");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean slim(File meta) {
        if (!meta.isFile()) {
            return false;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(meta), UTF_8)) {
            JsonElement model = new JsonParser().parse(reader).getAsJsonObject().get("model");
            return model != null && "slim".equals(model.getAsString());
        } catch (Exception e) {
            return false;
        }
    }

    /** {url, model} from a session profile's textures property, or null without a skin. */
    static String[] textures(JsonObject profile) {
        JsonArray properties = profile.getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String decoded = new String(java.util.Base64.getDecoder().decode(property.get("value").getAsString()), UTF_8);
            JsonObject textures = new JsonParser().parse(decoded).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            if (skin == null) {
                return null;
            }
            JsonObject metadata = skin.getAsJsonObject("metadata");
            String model = metadata != null && metadata.has("model") ? metadata.get("model").getAsString() : "classic";
            return new String[] {skin.get("url").getAsString().replaceFirst("^http://", "https://"), model};
        }
        return null;
    }

    private static JsonObject json(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "medirian-client");
        if (connection.getResponseCode() != 200) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "medirian-client");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static Skin read(File png, boolean slim) {
        try (InputStream in = new FileInputStream(png)) {
            return decode(readAll(in), slim);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Skin decode(byte[] bytes, boolean slim) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
            return null;
        }
        int[] argb = new int[64 * image.getHeight()];
        image.getRGB(0, 0, 64, image.getHeight(), argb, 0, 64);
        return new Skin(normalize(argb, image.getHeight()), slim, true, nextVersion());
    }

    private static synchronized int nextVersion() {
        return ++versions;
    }

    private static Skin defaultSkin() {
        try (InputStream in = PlayerSkins.class.getResourceAsStream("/assets/medirian/textures/gui/default_skin.png")) {
            if (in != null) {
                Skin skin = decode(readAll(in), false);
                if (skin != null) {
                    return new Skin(skin.argb, false, false, nextVersion());
                }
            }
        } catch (IOException e) {
            Log.warn("Medirian's default skin is missing");
        }
        return new Skin(new int[64 * 64], false, false, nextVersion());
    }

    /**
     * Prepares a skin exactly like Minecraft: an old 64×32 skin becomes 64×64 with the left arm and
     * leg mirrored from the right ones, its outer head layer is dropped when it has no transparent
     * pixel at all ("Notch's transparency hack"), and the inner layers are made opaque.
     */
    static int[] normalize(int[] source, int height) {
        int[] px = new int[64 * 64];
        System.arraycopy(source, 0, px, 0, Math.min(source.length, 64 * height));
        if (height == 32) {
            int[][] copies = {
                {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12}, {8, 20, 8, 32, 4, 12},
                {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4}, {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12},
                {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}
            };
            for (int[] c : copies) {
                for (int j = 0; j < c[5]; j++) {
                    for (int i = 0; i < c[4]; i++) {
                        px[(c[1] + c[3] + j) * 64 + c[0] + c[2] + (c[4] - 1 - i)] = px[(c[1] + j) * 64 + c[0] + i];
                    }
                }
            }
            boolean transparent = false;
            for (int y = 0; y < 16 && !transparent; y++) {
                for (int x = 32; x < 64; x++) {
                    if ((px[y * 64 + x] >>> 24) < 128) {
                        transparent = true;
                        break;
                    }
                }
            }
            if (!transparent) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 32; x < 64; x++) {
                        px[y * 64 + x] &= 0x00FFFFFF;
                    }
                }
            }
        }
        opaque(px, 0, 0, 32, 16);
        opaque(px, 0, 16, 64, 32);
        opaque(px, 16, 48, 48, 64);
        return px;
    }

    private static void opaque(int[] px, int x1, int y1, int x2, int y2) {
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                px[y * 64 + x] |= 0xFF000000;
            }
        }
    }
}
