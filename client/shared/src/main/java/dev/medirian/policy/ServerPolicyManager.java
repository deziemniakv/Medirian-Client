package dev.medirian.policy;

import dev.medirian.core.Log;
import dev.medirian.i18n.I18n;
import dev.medirian.module.Module;
import dev.medirian.module.ModuleManager;
import dev.medirian.notify.NotificationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link ServerPolicy}s: listed modules are switched off and locked while connected to the
 * server; leaving the server (or a policy without them) restores them as they were.
 */
public final class ServerPolicyManager {

    private final ModuleManager modules;
    private final NotificationManager notifications;
    /** Locked modules and whether they were enabled before. */
    private final Map<Module, Boolean> locked = new LinkedHashMap<Module, Boolean>();

    public ServerPolicyManager(ModuleManager modules, NotificationManager notifications) {
        this.modules = modules;
        this.notifications = notifications;
    }

    /** A policy payload arrived (client thread). */
    public void receive(byte[] payload) {
        ServerPolicy policy = ServerPolicy.parse(payload);
        if (policy == null) {
            Log.warn("Ignoring an invalid server policy ({} bytes)", payload == null ? 0 : payload.length);
            return;
        }
        apply(policy);
    }

    public void apply(ServerPolicy policy) {
        String reason = policy.message != null && !policy.message.trim().isEmpty()
                ? policy.message.trim() : I18n.tr("policy.reason", "Disabled by this server");
        // lift locks the new policy no longer contains
        for (Module module : new ArrayList<Module>(locked.keySet())) {
            if (!policy.disabledModules.contains(module.id())) {
                unlock(module);
            }
        }
        List<String> names = new ArrayList<String>();
        for (String id : policy.disabledModules) {
            Module module = modules.get(id);
            if (module == null) {
                continue;
            }
            if (!locked.containsKey(module)) {
                locked.put(module, module.isEnabled());
                module.setEnabled(false);
            }
            module.lock(reason);
            names.add(module.displayName());
        }
        Log.info("Server policy: disabled {}", names);
        if (!names.isEmpty()) {
            notifications.post(I18n.tr("policy.title", "Server rules"), reason + ": " + join(names), NotificationManager.Level.WARNING);
        }
    }

    /** Leaving the server lifts every restriction. */
    public void clear() {
        for (Module module : new ArrayList<Module>(locked.keySet())) {
            unlock(module);
        }
    }

    public boolean active() {
        return !locked.isEmpty();
    }

    private void unlock(Module module) {
        Boolean wasEnabled = locked.remove(module);
        module.lock(null);
        if (Boolean.TRUE.equals(wasEnabled)) {
            module.setEnabled(true);
        }
    }

    private static String join(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name);
        }
        return text.toString();
    }
}
