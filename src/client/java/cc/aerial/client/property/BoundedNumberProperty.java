package cc.aerial.client.property;

public final class BoundedNumberProperty extends Property<BoundedNumberProperty.Range> {
    public record Range(double low, double high) {
    }

    private final double minValue, maxValue, increment;

    public BoundedNumberProperty(String name, double defaultLow, double defaultHigh, double minValue, double maxValue, double increment) {
        super(name);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        setValue(new Range(defaultLow, defaultHigh));
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public double getIncrement() {
        return increment;
    }

    public double getLow() {
        return getValue().low();
    }

    public double getHigh() {
        return getValue().high();
    }

    public void setLow(double low) {
        setValue(new Range(low, getValue().high()));
    }

    public void setHigh(double high) {
        setValue(new Range(getValue().low(), high));
    }

    public double getRandomValue() {
        Range range = getValue();
        if (range.low() >= range.high()) {
            return range.low();
        }
        return range.low() + Math.random() * (range.high() - range.low());
    }

    @Override
    public void setValue(Range value) {
        double low = round(value.low());
        double high = round(value.high());
        super.setValue(new Range(Math.min(low, high), Math.max(low, high)));
    }

    private double round(double value) {
        double rounded = Math.round(value / increment) * increment;
        return Math.max(minValue, Math.min(maxValue, rounded));
    }
}
