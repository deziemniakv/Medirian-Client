package dev.medirian.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.medirian.core.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight localisation. English strings live next to the code as fallbacks, so a missing
 * translation never shows a raw key. Language files: {@code /medirian/lang/<code>.json}
 * (flat {@code "key": "text"} objects).
 */
public final class I18n {

    /** Supported languages; the first one is the default. */
    public enum Language implements dev.medirian.setting.ModeSetting.Labeled {
        EN_US("en_us", "English"),
        PL_PL("pl_pl", "Polski"),
        DE_DE("de_de", "Deutsch"),
        ES_ES("es_es", "Español");

        private final String code;
        private final String nativeName;

        Language(String code, String nativeName) {
            this.code = code;
            this.nativeName = nativeName;
        }

        public String code() {
            return code;
        }

        public String nativeName() {
            return nativeName;
        }

        @Override
        public String label() {
            return nativeName;
        }
    }

    private static volatile Map<String, String> table = Collections.emptyMap();
    private static volatile Language current = Language.EN_US;

    private I18n() {
    }

    public static Language language() {
        return current;
    }

    public static void setLanguage(Language language) {
        current = language;
        table = language == Language.EN_US ? Collections.<String, String>emptyMap() : load(language.code());
    }

    /** Translated text for {@code key}, or {@code fallback} when no translation exists. */
    public static String tr(String key, String fallback) {
        String value = table.get(key);
        return value != null ? value : fallback;
    }

    /** Translated text with {@code {0}}, {@code {1}}… placeholders replaced. */
    public static String tr(String key, String fallback, Object... args) {
        String text = tr(key, fallback);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    private static Map<String, String> load(String code) {
        InputStream in = I18n.class.getResourceAsStream("/medirian/lang/" + code + ".json");
        if (in == null) {
            Log.warn("Missing language file {}", code);
            return Collections.emptyMap();
        }
        try {
            Reader reader = new InputStreamReader(in, Charset.forName("UTF-8"));
            try {
                JsonObject object = new Gson().fromJson(reader, JsonObject.class);
                Map<String, String> map = new HashMap<String, String>();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return map;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            Log.error("Failed to load language {}", code, e);
            return Collections.emptyMap();
        }
    }
}
