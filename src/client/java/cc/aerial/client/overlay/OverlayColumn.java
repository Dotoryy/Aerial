package cc.aerial.client.overlay;

import java.util.Locale;
import java.util.function.Function;

public enum OverlayColumn {
    TAG("Tag", "Tag", 26.0f, false),
    STAR("Star", "Star", 30.0f, true),
    NAME("Name", "Name", 78.0f, true),
    FKDR("FKDR", "FKDR", 34.0f, true),
    WLR("WLR", "WLR", 30.0f, true),
    FINALS("Finals", "FK", 34.0f, true),
    WINS("Wins", "W", 34.0f, true),
    BEDS("Beds", "BB", 32.0f, false),
    WINSTREAK("Winstreak", "WS", 26.0f, false),
    DAILY_STARS("Daily Stars", "dS", 28.0f, false),
    DAILY_FKDR("Daily FKDR", "dFK", 34.0f, false),
    DAILY_WLR("Daily WLR", "dWL", 30.0f, false);

    public static final OverlayColumn[] VALUES = values();

    private final String label;
    private final String header;
    private final float width;
    private boolean enabled;

    OverlayColumn(String label, String header, float width, boolean enabledByDefault) {
        this.label = label;
        this.header = header;
        this.width = width;
        this.enabled = enabledByDefault;
    }

    public String getLabel() {
        return label;
    }

    public String getHeader() {
        return header;
    }

    public float getWidth() {
        return width;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String valueOf(BedwarsStats stats) {
        return switch (this) {
            case TAG -> tagOf(stats).exists() ? tagOf(stats).shortLabel() : "";
            case STAR -> String.valueOf(stats.getStar());
            case NAME -> stats.getName();
            case FKDR -> ratio(stats.getFkdr());
            case WLR -> ratio(stats.getWlr());
            case FINALS -> compact(stats.getFinalKills());
            case WINS -> compact(stats.getWins());
            case BEDS -> compact(stats.getBedsBroken());

            case WINSTREAK -> winstreak(stats);
            case DAILY_STARS -> signed(OverlayBordic.session(stats.getUuid()));
            case DAILY_FKDR -> session(stats, session -> ratio(session.fkdr()));
            case DAILY_WLR -> session(stats, session -> ratio(session.wlr()));
        };
    }

    public static PlayerTag tagOf(BedwarsStats stats) {
        return OverlayTags.get(stats.getName(), stats.getUuid());
    }

    private static String winstreak(BedwarsStats stats) {
        if (stats.isWinstreakKnown()) {
            return String.valueOf(stats.getWinstreak());
        }
        int tracked = OverlayBordic.winstreak(stats.getUuid());

        return tracked < 0 ? "-" : tracked + "*";
    }

    private static String session(BedwarsStats stats, Function<OverlayBordic.Session, String> format) {
        OverlayBordic.Session session = OverlayBordic.session(stats.getUuid());
        return session.present() ? format.apply(session) : "-";
    }

    private static String signed(OverlayBordic.Session session) {
        if (!session.present()) {
            return "-";
        }
        return session.starsGained() > 0 ? "+" + session.starsGained()
                : String.valueOf(session.starsGained());
    }

    public double sortValue(BedwarsStats stats) {
        return switch (this) {
            case TAG -> tagOf(stats).threat();
            case STAR -> stats.getStar();
            case FKDR -> stats.getFkdr();
            case WLR -> stats.getWlr();
            case FINALS -> stats.getFinalKills();
            case WINS -> stats.getWins();
            case BEDS -> stats.getBedsBroken();
            case WINSTREAK -> stats.isWinstreakKnown() ? stats.getWinstreak()
                    : OverlayBordic.winstreak(stats.getUuid());
            case DAILY_STARS -> OverlayBordic.session(stats.getUuid()).starsGained();
            case DAILY_FKDR -> OverlayBordic.session(stats.getUuid()).fkdr();
            case DAILY_WLR -> OverlayBordic.session(stats.getUuid()).wlr();
            case NAME -> 0;
        };
    }

    private static String ratio(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String compact(int value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        return String.format(Locale.ROOT, "%.1fk", value / 1000.0f);
    }

    @Override
    public String toString() {
        return label;
    }
}
