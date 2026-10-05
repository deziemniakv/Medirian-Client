package dev.medirian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.medirian.i18n.I18n;
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
        InputStream in = I18n.class.getResourceAsStream("/medirian/lang/" + code + ".json");
        assertNotNull(in, code + ".json is missing");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Set<String> keys = new TreeSet<String>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : new Gson().fromJson(reader, JsonObject.class).entrySet()) {
                keys.add(entry.getKey());
            }
            return keys;
        }
    }

    /** Every key the code asks for with a literal ({@code I18n.tr("key", ...)}) is translated. */
    @Test
    void everyKeyUsedInTheCodeIsTranslated() throws Exception {
        Set<String> translated = keys("pl_pl");
        Set<String> missing = new TreeSet<String>();
        java.util.regex.Pattern call = java.util.regex.Pattern.compile("I18n\\.tr\\(\\s*\"([a-zA-Z0-9_.]*[a-zA-Z0-9_])\"\\s*[,)]");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(java.nio.file.Paths.get("src/main/java"))) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                java.util.regex.Matcher matcher = call.matcher(new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8));
                while (matcher.find()) {
                    if (!translated.contains(matcher.group(1))) {
                        missing.add(matcher.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(new TreeSet<String>(), missing, "keys without a translation in pl_pl.json");
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
