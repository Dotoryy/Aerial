package cc.aerial.client.utility;

import java.util.Random;

public final class RandomUtility {
    private RandomUtility() {
    }

    public static final Random RANDOM = new Random();

    public static final double JOIN_RANDOM = RandomUtility.RANDOM.nextDouble();

    public static int getRandomInt(int min, int max) {
        return min == max ? min : min + RANDOM.nextInt(max - min);
    }

    public static int getRandomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    public static double getRandomDouble(double min, double max) {
        return getRandomDouble(min, max, RANDOM.nextDouble());
    }

    public static float getRandomFloat(float min, float max) {
        return getRandomFloat(min, max, RANDOM.nextFloat());
    }

    public static float getRandomFloat(float min, float max, float rand) {
        return min == max ? min : min + (max - min) * rand;
    }

    public static double getRandomDouble(double min, double max, double rand) {
        return min == max ? min : min + (max - min) * rand;
    }

    public static double getJoinRandomDouble(double min, double max) {
        return getRandomDouble(min, max, JOIN_RANDOM);
    }
}
