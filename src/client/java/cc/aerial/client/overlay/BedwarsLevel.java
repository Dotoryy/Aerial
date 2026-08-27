package cc.aerial.client.overlay;

public final class BedwarsLevel {
    private static final int EASY_LEVELS = 4;
    private static final int XP_PER_PRESTIGE = 487_000;
    private static final int LEVELS_PER_PRESTIGE = 100;
    private static final int XP_PER_LEVEL = 5_000;

    private static final int[] EASY_LEVEL_XP = {500, 1_000, 2_000, 3_500};

    private BedwarsLevel() {
    }

    public static int fromExperience(long experience) {
        if (experience <= 0) {
            return 0;
        }
        long prestiges = experience / XP_PER_PRESTIGE;
        int level = (int) (prestiges * LEVELS_PER_PRESTIGE);
        long remaining = experience - prestiges * XP_PER_PRESTIGE;

        for (int i = 0; i < EASY_LEVELS; i++) {
            if (remaining < EASY_LEVEL_XP[i]) {
                return level;
            }
            level++;
            remaining -= EASY_LEVEL_XP[i];
        }
        return level + (int) (remaining / XP_PER_LEVEL);
    }
}
