package cc.aerial.client.features;

import cc.aerial.client.binding.IBindable;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.property.Property;

import java.util.ArrayList;
import java.util.List;

public class Module implements IEventSubscriber, IBindable {
    private final String name;
    private final String id;

     private String description;
    private final ModuleCategory category;

    private boolean enabled;
    private boolean visible = true;

    private final List<Property<?>> propertyList = new ArrayList<>();

    protected Module(final String name, final String description, final ModuleCategory category) {
        this.name = name;
        this.id = name.toLowerCase().replace(' ', '_');
        this.description = description;
        this.category = category;

        EventDispatcher.subscribe(this);
    }

    public final void setEnabled(final boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            this.onEnable();
        } else {
            this.onDisable();
        }
    }

    public final void toggle() {
        this.setEnabled(!this.isEnabled());
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public final String getName() {
        return name;
    }

    public final String getId() {
        return id;
    }

     protected final void setDescription(final String description) {
         this.description = description;
     }

    public final String getDescription() {
        return description;
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final boolean isVisible() {
        return visible;
    }

    public final void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public final void addProperties(final Property<?>... properties) {
        for (final Property<?> property : properties) {
            if (property != null) {
                propertyList.add(property);
            }
        }
    }

    public final List<Property<?>> getPropertyList() {
        return propertyList;
    }

    public final Property<?>[] getProperties() {
        return propertyList.toArray(new Property<?>[0]);
    }

    public String getSuffix() {
        return null;
    }

    @Override
    public final boolean isHandlingEvents() {
        return enabled;
    }

    @Override
    public final void onBindingInteraction() {
        toggle();
    }
}
