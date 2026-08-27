package cc.aerial.client.overlay;

import java.util.Locale;

public record PlayerTag(Source source, String type, String reason) {
    public enum Source { URCHIN, SERAPH }

    public static final PlayerTag NONE = new PlayerTag(null, "", "");

    public boolean exists() {
        return source != null && !type.isEmpty();
    }

    public String shortLabel() {
        return switch (normalise(type)) {
            case "cheater", "cheating" -> "CHT";
            case "sniper", "sniping" -> "SNP";
            case "blacklisted", "blacklist" -> "BL";
            case "caution", "suspicious", "sus" -> "SUS";
            case "account_boosting", "boosting", "booster" -> "BST";
            case "ratting", "ratter" -> "RAT";
            default -> "TAG";
        };
    }

    public int color() {
        return switch (normalise(type)) {
            case "cheater", "cheating", "blacklisted", "blacklist" -> 0xFFFF5050;
            case "sniper", "sniping", "ratting", "ratter" -> 0xFFFF9F45;
            case "account_boosting", "boosting", "booster" -> 0xFFF2D06B;
            default -> 0xFFC8C8C8;
        };
    }

    public double threat() {
        if (!exists()) {
            return 0.0;
        }
        return switch (normalise(type)) {
            case "cheater", "cheating" -> 5.0;
            case "blacklisted", "blacklist" -> 4.5;
            case "sniper", "sniping" -> 3.5;
            case "ratting", "ratter" -> 3.0;
            case "account_boosting", "boosting", "booster" -> 2.0;
            case "caution", "suspicious", "sus" -> 1.0;
            default -> 0.5;
        };
    }

    public static PlayerTag worst(PlayerTag first, PlayerTag second) {
        if (!first.exists()) {
            return second;
        }
        if (!second.exists()) {
            return first;
        }
        return second.threat() > first.threat() ? second : first;
    }

    private static String normalise(String type) {
        return type.toLowerCase(Locale.ROOT).replace(' ', '_').trim();
    }
}
