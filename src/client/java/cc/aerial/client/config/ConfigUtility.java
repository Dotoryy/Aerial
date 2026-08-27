package cc.aerial.client.config;

import cc.aerial.client.AerialClient;
import cc.aerial.client.binding.BindRepository;
import cc.aerial.client.binding.BindingService;
import cc.aerial.client.binding.IBindable;
import cc.aerial.client.binding.InputType;
import cc.aerial.client.features.Module;
import cc.aerial.client.overlay.OverlayColumn;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.KeyProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.property.StringProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aerial");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigUtility() {
    }

    private static final String DEFAULT_NAME = "config";

    private static File file(String name) {
        return new File(Minecraft.getInstance().gameDirectory, "aerial" + File.separator + sanitize(name) + ".aerial");
    }

    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isEmpty() ? DEFAULT_NAME : cleaned;
    }

    public static void save() {
        save(DEFAULT_NAME);
    }

    public static boolean load() {
        return load(DEFAULT_NAME);
    }

    public static void save(String name) {
        try {
            File file = file(name);
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }

            JsonObject root = new JsonObject();
            root.add("modules", saveModules());
            root.add("binds", saveBinds());
            root.add("overlayColumns", saveOverlayColumns());

            Files.writeString(file.toPath(), GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean load(String name) {
        File file = file(name);
        if (!file.exists()) {
            return false;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject();
            if (root.has("modules")) {
                loadModules(root.getAsJsonArray("modules"));
            }
            if (root.has("binds")) {
                loadBinds(root.getAsJsonArray("binds"));
            }
            if (root.has("overlayColumns")) {
                loadOverlayColumns(root.getAsJsonArray("overlayColumns"));
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static JsonArray saveOverlayColumns() {
        JsonArray columns = new JsonArray();
        for (OverlayColumn column : OverlayColumn.VALUES) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", column.name());
            entry.addProperty("enabled", column.isEnabled());
            columns.add(entry);
        }
        return columns;
    }

    private static void loadOverlayColumns(JsonArray json) {
        List<OverlayColumn> ordered = new ArrayList<>();
        for (JsonElement element : json) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            OverlayColumn column = findColumn(entry.get("id").getAsString());
            if (column == null || ordered.contains(column)) {
                continue;
            }
            if (entry.has("enabled")) {
                column.setEnabled(entry.get("enabled").getAsBoolean());
            }
            ordered.add(column);
        }
        for (OverlayColumn column : OverlayColumn.values()) {
            if (!ordered.contains(column)) {
                ordered.add(column);
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            OverlayColumn.VALUES[i] = ordered.get(i);
        }
    }

    private static OverlayColumn findColumn(String id) {
        for (OverlayColumn column : OverlayColumn.values()) {
            if (column.name().equals(id)) {
                return column;
            }
        }
        return null;
    }

    private static JsonArray saveModules() {
        JsonArray modules = new JsonArray();
        for (Module module : AerialClient.getModuleRepository().getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("id", module.getId());
            moduleJson.addProperty("enabled", module.isEnabled());
            moduleJson.addProperty("visible", module.isVisible());
            JsonArray properties = new JsonArray();
            for (Property<?> property : module.getPropertyList()) {
                saveProperty(property, properties);
            }
            moduleJson.add("properties", properties);
            modules.add(moduleJson);
        }
        return modules;
    }

    private static void loadModules(JsonArray modules) {
        List<String> enabled = new ArrayList<>();
        for (JsonElement element : modules) {
            JsonObject moduleJson = element.getAsJsonObject();
            String id = moduleJson.get("id").getAsString();
            Module module = findModule(id);
            if (module == null) {
                continue;
            }
            if (moduleJson.has("enabled")) {
                boolean on = moduleJson.get("enabled").getAsBoolean();
                module.setEnabled(on);
                if (on) {
                    enabled.add(module.getName());
                }
            }
            if (moduleJson.has("visible")) {
                module.setVisible(moduleJson.get("visible").getAsBoolean());
            }
            if (moduleJson.has("properties")) {
                loadProperties(module.getPropertyList(), moduleJson.getAsJsonArray("properties"));
            }
        }
        LOGGER.info("Enabled from config ({}): {}", enabled.size(), String.join(", ", enabled));
    }

    private static Module findModule(String id) {
        for (Module module : AerialClient.getModuleRepository().getModules()) {
            if (module.getId().equals(id)) {
                return module;
            }
        }
        return null;
    }

    private static void saveProperty(Property<?> property, JsonArray out) {
        JsonObject json = new JsonObject();
        json.addProperty("name", property.getName());

        if (property instanceof GroupProperty group) {
            JsonArray children = new JsonArray();
            for (Property<?> child : group.getPropertyList()) {
                saveProperty(child, children);
            }
            json.add("properties", children);
        } else if (property instanceof MultipleBooleanProperty multi) {
            JsonArray children = new JsonArray();
            for (BooleanProperty child : multi.getValue()) {
                saveProperty(child, children);
            }
            json.add("properties", children);
        } else if (property instanceof BooleanProperty bool) {
            json.addProperty("value", bool.getValue());
        } else if (property instanceof NumberProperty number) {
            json.addProperty("value", number.getValue());
        } else if (property instanceof ModeProperty<?> mode) {
            json.addProperty("value", mode.getValue().name());
        } else if (property instanceof KeyProperty key) {
            json.addProperty("value", key.getValue());
        } else if (property instanceof StringProperty string) {
            json.addProperty("value", string.getValue());
        } else {
            return;
        }

        out.add(json);
    }

    private static void loadProperties(List<Property<?>> properties, JsonArray json) {
        for (JsonElement element : json) {
            JsonObject propertyJson = element.getAsJsonObject();
            String name = propertyJson.get("name").getAsString();
            for (Property<?> property : properties) {
                if (property.getName().equals(name)) {
                    loadProperty(property, propertyJson);
                    break;
                }
            }
        }
    }

    private static void loadProperty(Property<?> property, JsonObject json) {
        if (property instanceof GroupProperty group) {
            if (json.has("properties")) {
                loadProperties(group.getPropertyList(), json.getAsJsonArray("properties"));
            }
        } else if (property instanceof MultipleBooleanProperty multi) {
            if (json.has("properties")) {
                loadProperties(new ArrayList<Property<?>>(multi.getValue()), json.getAsJsonArray("properties"));
            }
        } else if (property instanceof BooleanProperty bool) {
            if (json.has("value")) {
                bool.setValue(json.get("value").getAsBoolean());
            }
        } else if (property instanceof NumberProperty number) {
            if (json.has("value")) {
                number.setValue(json.get("value").getAsDouble());
            }
        } else if (property instanceof ModeProperty<?> mode) {
            if (json.has("value")) {
                applyModeValue(mode, json.get("value").getAsString());
            }
        } else if (property instanceof KeyProperty key) {
            if (json.has("value")) {
                key.setValue(json.get("value").getAsInt());
            }
        } else if (property instanceof StringProperty string) {
            if (json.has("value")) {
                string.setValue(json.get("value").getAsString());
                string.cursorToEnd();
            }
        }
    }

    private static <T extends Enum<T>> void applyModeValue(ModeProperty<T> mode, String valueName) {
        for (T option : mode.getValues()) {
            if (option.name().equals(valueName)) {
                mode.setValue(option);
                return;
            }
        }
    }

    private static JsonArray saveBinds() {
        JsonArray binds = new JsonArray();
        for (Map.Entry<BindingService.BindKey, List<IBindable>> entry
                : BindRepository.INSTANCE.getBindingService().getBindingMap().entrySet()) {
            for (IBindable bindable : entry.getValue()) {
                if (bindable instanceof Module module) {
                    JsonObject bindJson = new JsonObject();
                    bindJson.addProperty("code", entry.getKey().code());
                    bindJson.addProperty("type", entry.getKey().type().name());
                    bindJson.addProperty("module", module.getId());
                    binds.add(bindJson);
                }
            }
        }
        return binds;
    }

    private static void loadBinds(JsonArray binds) {
        BindingService bindingService = BindRepository.INSTANCE.getBindingService();
        for (JsonElement element : binds) {
            JsonObject bindJson = element.getAsJsonObject();
            Module module = findModule(bindJson.get("module").getAsString());
            if (module == null) {
                continue;
            }
            int code = bindJson.get("code").getAsInt();
            InputType type = InputType.valueOf(bindJson.get("type").getAsString());
            bindingService.register(code, module, type);
        }
    }
}
