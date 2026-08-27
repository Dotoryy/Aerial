package cc.aerial.client.features.repository;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.UnknownModuleException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ModuleRepository {
    private final Map<Class<? extends Module>, Module> classToInstanceMap;
    private final Map<String, Module> idToInstanceMap;

    private final Map<String, Module> dynamicModules = new LinkedHashMap<>();

    private ModuleRepository(final Map<Class<? extends Module>, Module> classToInstanceMap, final Map<String, Module> idToInstanceMap) {
        this.classToInstanceMap = classToInstanceMap;
        this.idToInstanceMap = idToInstanceMap;
    }

    public void findModule(final String id, final Consumer<Module> moduleConsumer, final Consumer<UnknownModuleException> exceptionHandler) {
        try {
            moduleConsumer.accept(getModule(id));
        } catch (UnknownModuleException e) {
            exceptionHandler.accept(e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(final Class<T> moduleClass) {
        return (T) classToInstanceMap.get(moduleClass);
    }

    public Module getModule(final String id) throws UnknownModuleException {
        Module module = idToInstanceMap.get(id);
        if (module == null) {
            module = dynamicModules.get(id);
        }
        if (module == null) {
            throw new UnknownModuleException(id);
        }
        return module;
    }

    public void registerDynamic(final Module module) {
        dynamicModules.put(module.getId(), module);
    }

    public void unregisterDynamic(final Module module) {
        dynamicModules.remove(module.getId(), module);
    }

    public void clearDynamic() {
        dynamicModules.clear();
    }

    public Collection<Module> getModules() {
        if (dynamicModules.isEmpty()) {
            return classToInstanceMap.values();
        }
        final List<Module> all = new ArrayList<>(classToInstanceMap.values());
        all.addAll(dynamicModules.values());
        return all;
    }

    public List<Module> getModulesInCategory(final ModuleCategory category) {
        final List<Module> result = new ArrayList<>();
        for (final Module module : getModules()) {
            if (category.equals(module.getCategory())) {
                result.add(module);
            }
        }
        return result;
    }

    public static ModuleRepository fromModules(final Module... modules) {
        final Builder builder = builder();
        for (final Module module : modules) {
            builder.register(module);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Class<? extends Module>, Module> classToInstanceMap = new LinkedHashMap<>();
        private final Map<String, Module> idToInstanceMap = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder register(final Module module) {
            classToInstanceMap.put(module.getClass(), module);
            idToInstanceMap.put(module.getId(), module);
            return this;
        }

        public ModuleRepository build() {
            return new ModuleRepository(new LinkedHashMap<>(classToInstanceMap), new LinkedHashMap<>(idToInstanceMap));
        }
    }
}
