package cc.aerial.client.screen.animation;

import java.util.function.Function;

public enum Easing {
    LINEAR(x -> x),
    DECELERATE(x -> 1 - ((x - 1) * (x - 1))),
    EASE_OUT_SINE(x -> (float) Math.sin(x * Math.PI * 0.5)),
    EASE_OUT_EXPO(x -> x == 1 ? 1 : 1 - (float) Math.pow(2, -10 * x)),

    EASE_OUT_ELASTIC(x -> {
        if (x == 0.0f || x == 1.0f) {
            return x;
        }
        float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2, -10 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
    }),
    EASE_IN_BACK(x -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return c3 * x * x * x - c1 * x * x;
    }),
    EASE_OUT_QUINT(x -> 1 - (float) Math.pow(1 - x, 5)),

    EASE_IN_OUT_CUBIC(x -> x < 0.5f
            ? 4 * x * x * x
            : 1 - (float) Math.pow(-2 * x + 2, 3) / 2);

    private final Function<Float, Float> function;

    Easing(Function<Float, Float> function) {
        this.function = function;
    }

    public Function<Float, Float> getFunction() {
        return function;
    }
}
