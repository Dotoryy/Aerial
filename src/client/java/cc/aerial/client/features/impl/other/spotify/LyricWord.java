package cc.aerial.client.features.impl.other.spotify;

public final class LyricWord {
    public final String text;
    public final int startTime;
    public int endTime;

    public LyricWord(String text, int startTime) {
        this.text = text;
        this.startTime = startTime;
        this.endTime = startTime;
    }
}
