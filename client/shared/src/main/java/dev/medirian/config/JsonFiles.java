package dev.medirian.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.medirian.core.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** JSON file helpers with atomic writes (write to {@code .tmp}, then rename). */
public final class JsonFiles {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() {
    }

    /** Reads a JSON object; returns null when the file is missing or invalid (the error is logged). */
    public static JsonObject read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), UTF8);
            try {
                return GSON.fromJson(reader, JsonObject.class);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            Log.warn("Could not read {} ({}); a backup copy is kept as .broken", file, e.getMessage());
            backupBroken(file);
            return null;
        }
    }

    public static void write(File file, JsonObject json) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        File tmp = new File(file.getPath() + ".tmp");
        Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), UTF8);
        try {
            GSON.toJson(json, writer);
        } finally {
            writer.close();
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupBroken(File file) {
        File backup = new File(file.getPath() + ".broken");
        if (backup.exists()) {
            backup.delete();
        }
        file.renameTo(backup);
    }
}
