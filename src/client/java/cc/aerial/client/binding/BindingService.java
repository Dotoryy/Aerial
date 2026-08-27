package cc.aerial.client.binding;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BindingService {
    public record BindKey(int code, InputType type) {
    }

    private final Map<BindKey, List<IBindable>> bindingMap = new LinkedHashMap<>();

    public void register(int code, IBindable bindable, InputType type) {
        bindingMap.computeIfAbsent(new BindKey(code, type), key -> new ArrayList<>()).add(bindable);
    }

    public void clearBindings(IBindable bindable) {
        for (List<IBindable> bindables : bindingMap.values()) {
            bindables.remove(bindable);
        }
    }

    public void dispatch(int code, InputType type) {
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        List<IBindable> bindables = bindingMap.get(new BindKey(code, type));
        if (bindables == null || bindables.isEmpty()) {
            return;
        }

        for (IBindable bindable : List.copyOf(bindables)) {
            bindable.onBindingInteraction();
        }
    }

    public Map<BindKey, List<IBindable>> getBindingMap() {
        return bindingMap;
    }

    public BindKey getKeyFromBindable(IBindable bindable) {
        for (Map.Entry<BindKey, List<IBindable>> entry : bindingMap.entrySet()) {
            if (entry.getValue().contains(bindable)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
