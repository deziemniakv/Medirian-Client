package dev.meridian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.meridian.i18n.I18n;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every translation has exactly the keys of the Polish file (the most complete one). */
class LanguageFilesTest {

    private static Set<String> keys(String code) throws Exception {
        InputStream in = I18n.class.getResourceAsStream("/meridian/lang/" + code + ".json");
        assertNotNull(in, code + ".json is missing");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Set<String> keys = new TreeSet<String>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : new Gson().fromJson(reader, JsonObject.class).entrySet()) {
                keys.add(entry.getKey());
            }
            return keys;
        }
    }

    @Test
    void translationsAreComplete() throws Exception {
        Set<String> reference = keys("pl_pl");
        for (I18n.Language language : I18n.Language.values()) {
            if (language != I18n.Language.EN_US) {
                assertEquals(reference, keys(language.code()), language.code() + " differs from pl_pl");
            }
        }
    }
}
