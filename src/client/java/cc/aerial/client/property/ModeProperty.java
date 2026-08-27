package cc.aerial.client.property;

public final class ModeProperty<T extends Enum<T>> extends Property<T> {
    private final T[] values;

    public ModeProperty(String name, T value) {
        super(name);
        setValue(value);
        this.values = getEnumConstants();
    }

    @SuppressWarnings("unchecked")
    private T[] getEnumConstants() {
        return (T[]) getValue().getClass().getEnumConstants();
    }

    public T[] getValues() {
        return values;
    }

    public void setValueOrdinal(int value) {
        setValue(values[value]);
    }

    public void cycle(boolean forwards) {
        int currentIndex = getValue().ordinal();
        int nextIndex = (currentIndex + (forwards ? 1 : values.length - 1)) % values.length;
        setValueOrdinal(nextIndex);
    }

    public boolean is(T value) {
        return getValue() == value;
    }
}
