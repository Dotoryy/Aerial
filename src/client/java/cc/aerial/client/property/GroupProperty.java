package cc.aerial.client.property;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class GroupProperty extends Property<List<Property<?>>> {
    public GroupProperty(String name, Property<?>... children) {
        super(name);
        setValue(Arrays.stream(children).filter(Objects::nonNull).toList());
    }

    public List<Property<?>> getPropertyList() {
        return getValue();
    }
}
