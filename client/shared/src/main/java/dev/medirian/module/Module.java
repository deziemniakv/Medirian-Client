package dev.medirian.module;

import dev.medirian.event.Listener;
import dev.medirian.hud.HudElement;
import dev.medirian.i18n.I18n;
import dev.medirian.input.Key;
import dev.medirian.platform.Capability;
import dev.medirian.setting.KeySetting;
import dev.medirian.setting.Setting;
import dev.medirian.setting.SettingsOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Base class of every Medirian module.
 *
 * <p>A module owns typed settings, an optional key binding and an optional {@link HudElement}.
 * Event listeners registered with {@link #on} are subscribed only while the module is enabled,
 * so disabled modules cost nothing at runtime.
 *
 * <p>Modules contain version-independent logic only. Anything that must touch Minecraft is
 * expressed as a {@link Capability} the version adapter implements.
 */
public abstract class Module implements SettingsOwner {

    private final String id;
    private final String name;
    private final Category category;
    private final String description;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
    private final List<Subscription<?>> subscriptions = new ArrayList<Subscription<?>>();
    private final Set<Capability> requirements = EnumSet.noneOf(Capability.class);
    private final KeySetting keybind;
    private HudElement hud;
    private boolean enabled;
    private boolean enabledByDefault;
    /** Set while a server policy forbids the module (see dev.medirian.policy). */
    private String lockReason;
    ModuleManager manager;

    protected Module(String id, String name, Category category, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.keybind = new KeySetting("keybind", "Keybind", Key.NONE);
        this.keybind.attach(this);
        this.keybind.onPress(new KeySetting.Action() {
            @Override
            public void run() {
                onKeybindPressed();
            }
        });
        this.keybind.onRelease(new KeySetting.Action() {
            @Override
            public void run() {
                onKeybindReleased();
            }
        });
    }

    // ----- identity -----

    public final String id() {
        return id;
    }

    public String displayName() {
        return I18n.tr("module." + id + ".name", name);
    }

    public String displayDescription() {
        return I18n.tr("module." + id + ".desc", description);
    }

    public final Category category() {
        return category;
    }

    // ----- state -----

    public final boolean isEnabled() {
        return enabled;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enable) {
        if (enable == enabled || (enable && lockReason != null)) {
            return;
        }
        enabled = enable;
        if (enable) {
            subscribeAll();
            onEnable();
        } else {
            onDisable();
            unsubscribeAll();
        }
        if (manager != null) {
            manager.onModuleStateChanged(this);
        }
    }

    /** Why the module cannot be enabled right now (a server's message), or null. */
    public final String lockReason() {
        return lockReason;
    }

    public final boolean isLocked() {
        return lockReason != null;
    }

    /** Locks the module off ({@code reason} non-null) or lifts the lock; used by server policies. */
    public final void lock(String reason) {
        lockReason = reason;
        if (manager != null) {
            manager.onModuleStateChanged(this);
        }
    }

    public final boolean enabledByDefault() {
        return enabledByDefault;
    }

    /** Marks the module as enabled in the Default profile. Call from the constructor. */
    protected final void enableByDefault() {
        enabledByDefault = true;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    // ----- key binding -----

    public final KeySetting keybind() {
        return keybind;
    }

    /** Sets the module's default key binding. Call from the constructor. */
    protected final void defaultKeybind(Key key) {
        keybind.setDefaultKey(key);
    }

    /** Default action: toggle the module. Hold-style modules (zoom, freelook) override this. */
    protected void onKeybindPressed() {
        toggle();
    }

    protected void onKeybindReleased() {
    }

    /** Whether the keybind should fire while the module is disabled (true for toggle-style modules). */
    public boolean keybindWorksWhenDisabled() {
        return true;
    }

    // ----- capabilities -----

    protected final void requires(Capability... capabilities) {
        Collections.addAll(requirements, capabilities);
    }

    public final Set<Capability> requirements() {
        return requirements;
    }

    // ----- HUD -----

    public final HudElement hud() {
        return hud;
    }

    public final boolean hasHud() {
        return hud != null;
    }

    protected final <H extends HudElement> H hud(H element) {
        this.hud = element;
        return element;
    }

    // ----- settings -----

    /** Adds a setting owned by this module (HUD elements use this to register their style settings). */
    public final <S extends Setting<?>> S add(S setting) {
        setting.attach(this);
        settings.add(setting);
        return setting;
    }

    @Override
    public final String settingsNamespace() {
        return id;
    }

    @Override
    public final List<Setting<?>> settings() {
        return settings;
    }

    public final Setting<?> setting(String settingId) {
        for (Setting<?> setting : settings) {
            if (setting.id().equals(settingId)) {
                return setting;
            }
        }
        return null;
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        if (manager != null) {
            manager.onModuleStateChanged(this);
        }
    }

    // ----- events -----

    protected final <E> void on(Class<E> type, Listener<E> listener) {
        Subscription<E> subscription = new Subscription<E>(type, listener);
        subscriptions.add(subscription);
        if (enabled && manager != null) {
            subscription.subscribe(manager);
        }
    }

    private void subscribeAll() {
        if (manager == null) {
            return;
        }
        for (Subscription<?> subscription : subscriptions) {
            subscription.subscribe(manager);
        }
    }

    private void unsubscribeAll() {
        if (manager == null) {
            return;
        }
        for (Subscription<?> subscription : subscriptions) {
            subscription.unsubscribe(manager);
        }
    }

    void attachTo(ModuleManager moduleManager) {
        this.manager = moduleManager;
        if (enabled) {
            subscribeAll();
        }
    }

    @Override
    public String toString() {
        return "Module[" + id + "]";
    }

    private static final class Subscription<E> {
        final Class<E> type;
        final Listener<E> listener;

        Subscription(Class<E> type, Listener<E> listener) {
            this.type = type;
            this.listener = listener;
        }

        void subscribe(ModuleManager manager) {
            manager.events().subscribe(type, listener);
        }

        void unsubscribe(ModuleManager manager) {
            manager.events().unsubscribe(type, listener);
        }
    }
}
