package cc.aerial.client.features.impl.other.spotify;

import java.util.ArrayList;
import java.util.List;

public final class LyricLine {
    public final int startTime;
    public final String text;
    public List<LyricWord> words;
    public final boolean wordTimed;

    public LyricLine(int startTime, String text, List<LyricWord> words, boolean wordTimed) {
        this.startTime = startTime;
        this.text = text;
        this.words = words == null ? new ArrayList<>() : words;
        this.wordTimed = wordTimed;
    }
}
