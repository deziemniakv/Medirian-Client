package dev.meridian.setting;

import com.google.gson.JsonElement;

/**
 * A button shown among settings (e.g. "Open screenshots folder"). Not persisted:
 * {@link #toJson()} returns null and persistence skips it.
 */
public final class ActionSetting extends Setting<Boolean> {

    private final Runnable action;
    private final String buttonLabel;

    public ActionSetting(String id, String name, String buttonLabel, Runnable action) {
        super(id, name, Boolean.FALSE);
        this.action = action;
        this.buttonLabel = buttonLabel;
    }

    public void run() {
        action.run();
    }

    public String buttonLabel() {
        String ownerId = owner() == null ? "global" : owner().settingsNamespace();
        return dev.meridian.i18n.I18n.tr("setting." + ownerId + "." + id() + ".button", buttonLabel);
    }

    @Override
    public JsonElement toJson() {
        return null;
    }

    @Override
    public void fromJson(JsonElement json) {
        // not persisted
    }
}
