package cc.aerial.client.property;

import java.util.function.BooleanSupplier;

public class Property<T> {
    private final String name;
    private T value;
    private BooleanSupplier hiddenSupplier;

    protected Property(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public final <R extends Property<T>> R hideIf(BooleanSupplier hiddenSupplier) {
        this.hiddenSupplier = hiddenSupplier;
        return (R) this;
    }

    public final String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public final boolean isHidden() {
        return this.hiddenSupplier != null && this.hiddenSupplier.getAsBoolean();
    }
}
