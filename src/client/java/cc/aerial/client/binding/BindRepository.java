package cc.aerial.client.binding;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.press.KeyPressEvent;
import cc.aerial.client.event.impl.press.MousePressEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BindRepository implements IEventSubscriber {
    public static final BindRepository INSTANCE = new BindRepository();

    private static final String GLFW_KEY_PREFIX = "GLFW_KEY_";

    private final BindingService bindingService = new BindingService();
    private final Map<String, Integer> namedBindingMap = new LinkedHashMap<>();

    private BindRepository() {
        try {
            for (Field field : GLFW.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class
                        && field.getName().startsWith(GLFW_KEY_PREFIX)) {
                    namedBindingMap.put(field.getName().substring(GLFW_KEY_PREFIX.length()), field.getInt(null));
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < 10; i++) {
            namedBindingMap.put("MOUSE_" + i, i);
        }

        EventDispatcher.subscribe(this);
    }

    public BindingService getBindingService() {
        return bindingService;
    }

    public Map<String, Integer> getNamedBindingMap() {
        return namedBindingMap;
    }

    public String getNameFromInteger(int code, InputType type) {
        for (Map.Entry<String, Integer> entry : namedBindingMap.entrySet()) {
            boolean isMouseName = entry.getKey().startsWith("MOUSE_");
            if (entry.getValue() == code && isMouseName == (type == InputType.MOUSE)) {
                return entry.getKey();
            }
        }
        return String.valueOf(code);
    }

    @Subscribe
    public void onKeyPress(KeyPressEvent event) {
        if (!event.isPressed()) {
            return;
        }
        bindingService.dispatch(event.getInteractionCode(), InputType.KEYBOARD);
    }

    @Subscribe
    public void onMousePress(MousePressEvent event) {
        if (!event.isPressed()) {
            return;
        }
        bindingService.dispatch(event.getInteractionCode(), InputType.MOUSE);
    }
}
