package dev.meridian.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.meridian.core.Log;
import dev.meridian.core.MeridianHome;
import dev.meridian.hud.HudElement;
import dev.meridian.module.Module;
import dev.meridian.module.ModuleManager;
import dev.meridian.setting.ActionSetting;
import dev.meridian.setting.Setting;
import dev.meridian.setting.SettingsOwner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loads and saves client configuration.
 *
 * <ul>
 *   <li>{@code config/client.json} — {@link GlobalSettings} and the active profile name.</li>
 *   <li>{@code config/profiles/<slug>.json} — one configuration profile: module states, keybinds,
 *       module settings, HUD layout and {@link ProfileSettings}.</li>
 * </ul>
 *
 * <p>Changes are debounced (1 s) and written on a background thread from a snapshot taken on the
 * client thread, so saving never stalls a frame. Modules that exist in the file but not in the
 * running version (e.g. a 1.21-only module while playing 1.8.9) are preserved untouched.
 */
public final class ConfigManager {

    public static final int FORMAT_VERSION = 1;
    public static final String DEFAULT_PROFILE = "Default";
    private static final long SAVE_DEBOUNCE_MS = 1000;

    private final MeridianHome home;
    private final ModuleManager modules;
    private final GlobalSettings global;
    private final ProfileSettings profileSettings;
    private final ExecutorService io;

    private String activeProfile = DEFAULT_PROFILE;
    private JsonObject preservedModules = new JsonObject();
    private boolean loading;
    private boolean dirty;
    private long dirtySinceMs;
    private ProfileListener profileListener;

    /** Notified after a profile has been applied. */
    public interface ProfileListener {
        void profileLoaded(String name);
    }

    public ConfigManager(MeridianHome home, ModuleManager modules, GlobalSettings global, ProfileSettings profileSettings) {
        this.home = home;
        this.modules = modules;
        this.global = global;
        this.profileSettings = profileSettings;
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Meridian-Config-IO");
            thread.setDaemon(true);
            return thread;
        });
        Runnable dirtyMarker = this::markDirty;
        modules.setChangeListener(dirtyMarker);
        global.setChangeListener(dirtyMarker);
        profileSettings.setChangeListener(dirtyMarker);
    }

    public void setProfileListener(ProfileListener listener) {
        this.profileListener = listener;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Loads global settings and the requested (or last active) profile, creating the built-in
     * preset profiles on first run.
     */
    public void init(String requestedProfile) {
        home.profilesDir().mkdirs();
        JsonObject client = JsonFiles.read(clientFile());
        if (client != null) {
            loading = true;
            readSettings(global, client.getAsJsonObject("settings"));
            loading = false;
            if (client.has("activeProfile")) {
                activeProfile = client.get("activeProfile").getAsString();
            }
        }
        global.applyAll();
        for (Preset preset : Preset.values()) {
            if (!profileFile(preset.profileName()).isFile()) {
                applyPreset(preset);
                writeNow(profileFile(preset.profileName()), snapshotProfile(preset.profileName()));
            }
        }
        String target = requestedProfile != null && exists(requestedProfile) ? requestedProfile : activeProfile;
        if (!loadProfile(target, false)) {
            loadProfile(DEFAULT_PROFILE, false);
        }
    }

    /** Saves pending changes; call when the game closes. */
    public void shutdown() {
        if (dirty) {
            saveNow();
        }
        io.shutdown();
        try {
            io.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Call every client tick; writes debounced changes. */
    public void tick(long nowMs) {
        if (dirty && nowMs - dirtySinceMs >= SAVE_DEBOUNCE_MS) {
            saveNow();
        }
    }

    public void markDirty() {
        if (loading) {
            return;
        }
        if (!dirty) {
            dirtySinceMs = System.currentTimeMillis();
        }
        dirty = true;
    }

    /** Snapshots the current state on this thread and writes it asynchronously. */
    public void saveNow() {
        dirty = false;
        final JsonObject profile = snapshotProfile(activeProfile);
        final JsonObject client = snapshotClient();
        final File profileFile = profileFile(activeProfile);
        final File clientFile = clientFile();
        io.execute(() -> {
            writeNow(profileFile, profile);
            writeNow(clientFile, client);
        });
    }

    // ------------------------------------------------------------------ profiles

    public String activeProfile() {
        return activeProfile;
    }

    /** Names of all profiles on disk, presets first, then custom profiles alphabetically. */
    public List<String> profiles() {
        List<String> custom = new ArrayList<String>();
        File[] files = home.profilesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".json")) {
                    continue;
                }
                JsonObject json = JsonFiles.read(file);
                String name = json != null && json.has("name") ? json.get("name").getAsString()
                        : file.getName().substring(0, file.getName().length() - 5);
                if (Preset.byName(name) == null) {
                    custom.add(name);
                }
            }
        }
        Collections.sort(custom, String.CASE_INSENSITIVE_ORDER);
        List<String> result = new ArrayList<String>();
        for (Preset preset : Preset.values()) {
            if (profileFile(preset.profileName()).isFile()) {
                result.add(preset.profileName());
            }
        }
        result.addAll(custom);
        return result;
    }

    public boolean exists(String name) {
        return name != null && profileFile(name).isFile();
    }

    /** Applies a profile from disk. When {@code saveCurrent} is true pending changes are saved first. */
    public boolean loadProfile(String name, boolean saveCurrent) {
        if (saveCurrent && dirty) {
            saveNow();
        }
        File file = profileFile(name);
        JsonObject json = JsonFiles.read(file);
        if (json == null) {
            Preset preset = Preset.byName(name);
            if (preset == null) {
                Log.warn("Profile '{}' not found", name);
                return false;
            }
            applyPreset(preset);
            json = snapshotProfile(preset.profileName());
        }
        loading = true;
        try {
            resetToDefaults();
            applyProfileJson(json);
        } finally {
            loading = false;
        }
        activeProfile = json.has("name") ? json.get("name").getAsString() : name;
        dirty = false;
        final JsonObject client = snapshotClient();
        io.execute(() -> writeNow(clientFile(), client));
        Log.info("Loaded configuration profile '{}'", activeProfile);
        if (profileListener != null) {
            profileListener.profileLoaded(activeProfile);
        }
        return true;
    }

    /** Validates a user supplied profile name; returns an error message key or null when valid. */
    public String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "profile.error.empty";
        }
        String trimmed = name.trim();
        if (trimmed.length() > 24) {
            return "profile.error.long";
        }
        if (!trimmed.matches("[A-Za-z0-9 _\\-]+")) {
            return "profile.error.chars";
        }
        if (exists(trimmed)) {
            return "profile.error.exists";
        }
        return null;
    }

    /** Creates a new profile as a copy of the current state. Returns false when the name is invalid. */
    public boolean createProfile(String name) {
        if (validateName(name) != null) {
            return false;
        }
        String trimmed = name.trim();
        writeNow(profileFile(trimmed), snapshotProfile(trimmed));
        return true;
    }

    /** A profile's JSON for copying elsewhere (the active one includes unsaved changes), or null. */
    public JsonObject exportProfile(String name) {
        if (name.equalsIgnoreCase(activeProfile)) {
            return snapshotProfile(activeProfile);
        }
        return JsonFiles.read(profileFile(name));
    }

    /**
     * Stores a profile received from elsewhere (Meridian cloud), replacing a local profile of the
     * same name; the active profile is applied right away. Returns false for an invalid name.
     */
    public boolean importProfile(String name, JsonObject json) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 24 || !trimmed.matches("[A-Za-z0-9 _\\-]+") || json == null) {
            return false;
        }
        json.addProperty("name", trimmed);
        writeNow(profileFile(trimmed), json);
        if (trimmed.equalsIgnoreCase(activeProfile)) {
            loadProfile(trimmed, false);
        }
        return true;
    }

    /** Deletes a profile. The Default profile cannot be deleted. Switches to Default when active. */
    public boolean deleteProfile(String name) {
        if (DEFAULT_PROFILE.equalsIgnoreCase(name) || !exists(name)) {
            return false;
        }
        boolean wasActive = name.equalsIgnoreCase(activeProfile);
        if (!profileFile(name).delete()) {
            return false;
        }
        if (wasActive) {
            dirty = false;
            loadProfile(DEFAULT_PROFILE, false);
        }
        return true;
    }

    /** Resets every HUD element of the active profile to its default position and scale. */
    public void resetHudLayout() {
        for (Module module : modules.all()) {
            if (module.hasHud()) {
                module.hud().resetLayout();
            }
        }
        markDirty();
    }

    // ------------------------------------------------------------------ presets

    /** Resets all modules and profile settings, then applies the preset's differences. */
    public void applyPreset(Preset preset) {
        boolean wasLoading = loading;
        loading = true;
        try {
            resetToDefaults();
            preset.apply(modules, profileSettings);
        } finally {
            loading = wasLoading;
        }
    }

    private void resetToDefaults() {
        preservedModules = new JsonObject();
        for (Setting<?> setting : profileSettings.settings()) {
            setting.reset();
        }
        for (Module module : modules.all()) {
            for (Setting<?> setting : module.settings()) {
                setting.reset();
            }
            module.keybind().reset();
            if (module.hasHud()) {
                module.hud().resetLayout();
            }
            module.setEnabled(module.enabledByDefault());
        }
    }

    // ------------------------------------------------------------------ serialization

    JsonObject snapshotProfile(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.addProperty("name", name);
        json.add("settings", writeSettings(profileSettings));
        JsonObject moduleJson = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.add("keybind", module.keybind().toJson());
            entry.add("settings", writeSettings(module));
            HudElement hud = module.hud();
            if (hud != null) {
                entry.add("hud", hud.layoutToJson());
            }
            moduleJson.add(module.id(), entry);
        }
        for (Map.Entry<String, JsonElement> preserved : preservedModules.entrySet()) {
            if (!moduleJson.has(preserved.getKey())) {
                moduleJson.add(preserved.getKey(), preserved.getValue());
            }
        }
        json.add("modules", moduleJson);
        return json;
    }

    private JsonObject snapshotClient() {
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.addProperty("activeProfile", activeProfile);
        json.add("settings", writeSettings(global));
        return json;
    }

    private void applyProfileJson(JsonObject json) {
        JsonObject migrated = ConfigMigrations.migrate(json);
        readSettings(profileSettings, migrated.getAsJsonObject("settings"));
        JsonObject moduleJson = migrated.getAsJsonObject("modules");
        if (moduleJson == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : moduleJson.entrySet()) {
            Module module = modules.get(entry.getKey());
            if (module == null) {
                preservedModules.add(entry.getKey(), entry.getValue());
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject data = entry.getValue().getAsJsonObject();
            readSettings(module, data.getAsJsonObject("settings"));
            if (data.has("keybind")) {
                module.keybind().fromJson(data.get("keybind"));
            }
            if (data.has("hud") && data.get("hud").isJsonObject() && module.hasHud()) {
                module.hud().layoutFromJson(data.getAsJsonObject("hud"));
            }
            if (data.has("enabled") && data.get("enabled").isJsonPrimitive()) {
                module.setEnabled(data.get("enabled").getAsBoolean());
            }
        }
    }

    static JsonObject writeSettings(SettingsOwner owner) {
        JsonObject json = new JsonObject();
        for (Setting<?> setting : owner.settings()) {
            if (setting instanceof ActionSetting) {
                continue;
            }
            JsonElement value = setting.toJson();
            if (value != null) {
                json.add(setting.id(), value);
            }
        }
        return json;
    }

    static void readSettings(SettingsOwner owner, JsonObject json) {
        if (json == null) {
            return;
        }
        for (Setting<?> setting : owner.settings()) {
            JsonElement value = json.get(setting.id());
            if (value != null && !(setting instanceof ActionSetting)) {
                try {
                    setting.fromJson(value);
                } catch (RuntimeException e) {
                    Log.warn("Ignoring invalid value for {}.{}", owner.settingsNamespace(), setting.id());
                }
            }
        }
    }

    private static void writeNow(File file, JsonObject json) {
        try {
            JsonFiles.write(file, json);
        } catch (IOException e) {
            Log.error("Failed to save {}", file, e);
        }
    }

    // ------------------------------------------------------------------ files

    private File clientFile() {
        return new File(home.configDir(), "client.json");
    }

    File profileFile(String name) {
        return new File(home.profilesDir(), slug(name) + ".json");
    }

    /** File name for a profile: lower case, non-alphanumerics replaced with '-'. */
    static String slug(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "profile" : slug;
    }
}
