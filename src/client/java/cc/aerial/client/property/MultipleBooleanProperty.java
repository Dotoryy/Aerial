package cc.aerial.client.property;

import java.util.Arrays;
import java.util.List;

public final class MultipleBooleanProperty extends Property<List<BooleanProperty>> {
    public MultipleBooleanProperty(String name, BooleanProperty... booleanProperties) {
        super(name);
        setValue(Arrays.asList(booleanProperties));
    }

    public BooleanProperty getProperty(String name) {
        List<BooleanProperty> properties = getValue();
        for (int i = 0; i < properties.size(); i++) {
            BooleanProperty property = properties.get(i);
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }
}
