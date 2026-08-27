package cc.aerial.client.overlay;

import java.util.UUID;

public final class BedwarsStats {
    public enum State { LOADING, LOADED, NICKED, ERROR }

    private final State state;
    private final String name;
    private final UUID uuid;

    private final int star;
    private final int finalKills;
    private final int finalDeaths;
    private final int wins;
    private final int losses;
    private final int bedsBroken;
    private final int winstreak;

    private final boolean winstreakKnown;
    private final String rankPrefix;

    private final float fkdr;
    private final float wlr;

    private BedwarsStats(State state, String name, UUID uuid, int star, int finalKills, int finalDeaths,
                         int wins, int losses, int bedsBroken, int winstreak, boolean winstreakKnown,
                         String rankPrefix) {
        this.state = state;
        this.name = name;
        this.uuid = uuid;
        this.star = star;
        this.finalKills = finalKills;
        this.finalDeaths = finalDeaths;
        this.wins = wins;
        this.losses = losses;
        this.bedsBroken = bedsBroken;
        this.winstreak = winstreak;
        this.winstreakKnown = winstreakKnown;
        this.rankPrefix = rankPrefix;

        this.fkdr = finalDeaths == 0 ? finalKills : (float) finalKills / finalDeaths;
        this.wlr = losses == 0 ? wins : (float) wins / losses;
    }

    public static BedwarsStats loading(String name, UUID uuid) {
        return new BedwarsStats(State.LOADING, name, uuid, 0, 0, 0, 0, 0, 0, 0, false, "");
    }

    public static BedwarsStats nicked(String name, UUID uuid) {
        return new BedwarsStats(State.NICKED, name, uuid, 0, 0, 0, 0, 0, 0, 0, false, "");
    }

    public static BedwarsStats error(String name, UUID uuid) {
        return new BedwarsStats(State.ERROR, name, uuid, 0, 0, 0, 0, 0, 0, 0, false, "");
    }

    public static BedwarsStats loaded(String name, UUID uuid, int star, int finalKills, int finalDeaths,
                                      int wins, int losses, int bedsBroken, int winstreak,
                                      boolean winstreakKnown, String rankPrefix) {
        return new BedwarsStats(State.LOADED, name, uuid, star, finalKills, finalDeaths, wins, losses,
                bedsBroken, winstreak, winstreakKnown, rankPrefix);
    }

    public State getState() {
        return state;
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public boolean isLoaded() {
        return state == State.LOADED;
    }

    public boolean isNicked() {
        return state == State.NICKED;
    }

    public boolean isError() {
        return state == State.ERROR;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getStar() {
        return star;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public int getFinalDeaths() {
        return finalDeaths;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getBedsBroken() {
        return bedsBroken;
    }

    public int getWinstreak() {
        return winstreak;
    }

    public boolean isWinstreakKnown() {
        return winstreakKnown;
    }

    public String getRankPrefix() {
        return rankPrefix;
    }

    public float getFkdr() {
        return fkdr;
    }

    public float getWlr() {
        return wlr;
    }
}
