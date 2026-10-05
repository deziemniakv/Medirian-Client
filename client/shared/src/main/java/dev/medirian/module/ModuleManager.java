package dev.medirian.module;

import dev.medirian.core.Log;
import dev.medirian.event.EventBus;
import dev.medirian.platform.Capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Central registry of modules.
 *
 * <p>Modules whose {@link Module#requirements()} are not implemented by the running version
 * adapter are rejected at registration, so the UI only ever lists modules that work.
 */
public final class ModuleManager {

    /** Decides whether the running adapter implements a capability. */
    public interface CapabilityCheck {
        boolean supports(Capability capability);
    }

    private final EventBus events;
    private final CapabilityCheck capabilities;
    private final Map<String, Module> modules = new LinkedHashMap<String, Module>();
    private final List<Module> unsupported = new ArrayList<Module>();
    private Runnable changeListener;

    public ModuleManager(EventBus events, CapabilityCheck capabilities) {
        this.events = events;
        this.capabilities = capabilities;
    }

    /** Registers a module; returns false (and skips it) when the adapter lacks a capability. */
    public boolean register(Module module) {
        Set<Capability> missing = missingCapabilities(module);
        if (!missing.isEmpty()) {
            unsupported.add(module);
            Log.info("Module '{}' not available in this version (missing {})", module.id(), missing);
            return false;
        }
        if (modules.containsKey(module.id())) {
            throw new IllegalStateException("Duplicate module id " + module.id());
        }
        modules.put(module.id(), module);
        module.attachTo(this);
        return true;
    }

    private Set<Capability> missingCapabilities(Module module) {
        Set<Capability> missing = java.util.EnumSet.noneOf(Capability.class);
        for (Capability capability : module.requirements()) {
            if (!capabilities.supports(capability)) {
                missing.add(capability);
            }
        }
        return missing;
    }

    public EventBus events() {
        return events;
    }

    public Module get(String id) {
        return modules.get(id);
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        for (Module module : modules.values()) {
            if (type.isInstance(module)) {
                return (M) module;
            }
        }
        return null;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<Module>(modules.values()));
    }

    /** Modules that were not registered because the running version lacks capabilities. */
    public List<Module> unsupported() {
        return Collections.unmodifiableList(unsupported);
    }

    public List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<Module>();
        for (Module module : modules.values()) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    /** Case-insensitive search over localised names, ids and descriptions. */
    public List<Module> search(String query, Category category) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<Module>();
        for (Module module : modules.values()) {
            if (category != null && module.category() != category) {
                continue;
            }
            if (q.isEmpty()
                    || module.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || module.id().contains(q)
                    || module.displayDescription().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(module);
            }
        }
        return result;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    void onModuleStateChanged(Module module) {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
