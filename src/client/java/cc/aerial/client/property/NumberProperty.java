package cc.aerial.client.property;

public final class NumberProperty extends Property<Double> {
    private final double minValue, maxValue, increment;

    public NumberProperty(String name, double defaultValue, double minValue, double maxValue, double increment) {
        super(name);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
        setValue(defaultValue);
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

    @Override
    public void setValue(Double value) {
        double rounded = Math.round(value / increment) * increment;
        double clamped = Math.max(minValue, Math.min(maxValue, rounded));
        super.setValue(clamped);
    }
}
