package cc.aerial.client.property;

public final class BooleanProperty extends Property<Boolean> {
    public BooleanProperty(String name, boolean value) {
        super(name);
        setValue(value);
    }

    public void toggle() {
        setValue(!getValue());
    }

    @Override
    public Boolean getValue() {
        return super.getValue() && !isHidden();
    }
}
