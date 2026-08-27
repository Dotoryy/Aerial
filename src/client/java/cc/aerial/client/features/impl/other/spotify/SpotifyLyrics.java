package cc.aerial.client.features.impl.other.spotify;

import cc.aerial.client.overlay.OverlayHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpotifyLyrics {
    private SpotifyLyrics() {
    }

    record Result(List<LyricLine> syncedLines, List<String> plainLines) {
        static final Result EMPTY = new Result(List.of(), List.of());

        boolean isEmpty() {
            return syncedLines.isEmpty() && plainLines.isEmpty();
        }
    }

    static Result fetch(String title, String artist, String provider, String endpointUrl,
                        String endpointHeader, int durationMs, boolean debug) {
        String primaryArtist = artist.contains(",") ? artist.split(",")[0].trim() : artist.trim();

        if (!"LRCLIB".equals(provider)) {
            Result viaEndpoint = tryEndpoint(title, artist, primaryArtist, provider, endpointUrl, endpointHeader, debug);
            if (viaEndpoint != null) {
                return viaEndpoint;
            }
        }

        JsonObject exact = lrclibExact(title, primaryArtist, durationMs, debug);
        JsonObject match = exact;
        if (match == null) {
            JsonObject searched = lrclibSearch(title, primaryArtist, durationMs, debug);
            if (searched != null) {
                boolean emptyBoth = OverlayHttp.string(searched, "syncedLyrics").isEmpty()
                        && OverlayHttp.string(searched, "plainLyrics").isEmpty();
                match = emptyBoth
                        ? lrclibExact(OverlayHttp.string(searched, "trackName"), OverlayHttp.string(searched, "artistName"), durationMs, debug)
                        : searched;
            }
        }

        if (match == null) {
            if (debug) {
                cc.aerial.client.utility.ChatUtility.print("LRCLIB: no lyrics for " + title + " - " + primaryArtist);
            }
            return Result.EMPTY;
        }

        String synced = OverlayHttp.string(match, "syncedLyrics");
        if (!synced.isEmpty()) {
            List<LyricLine> lines = parseLRC(synced, durationMs);
            if (!lines.isEmpty()) {
                return new Result(lines, List.of());
            }
        }
        String plain = OverlayHttp.string(match, "plainLyrics");
        if (!plain.isEmpty()) {
            return new Result(List.of(), Arrays.asList(plain.split("\\r?\\n")));
        }
        return Result.EMPTY;
    }

    private static Result tryEndpoint(String title, String artist, String primaryArtist, String provider,
                                       String endpointUrl, String endpointHeader, boolean debug) {
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            return null;
        }
        try {
            String url = endpointUrl
                    .replace("{title}", OverlayHttp.encode(title == null ? "" : title))
                    .replace("{artist}", OverlayHttp.encode(primaryArtist))
                    .replace("{artists}", OverlayHttp.encode(artist == null ? "" : artist))
                    .replace("{spotifyId}", "");
            String body = fetchRaw(url, endpointHeader, debug, "Custom lyrics");
            if (body == null) {
                return null;
            }

            String trimmed = body.trim();
            if (trimmed.startsWith("{")) {
                JsonElement parsed = JsonParser.parseString(trimmed);
                if (!parsed.isJsonObject()) {
                    return null;
                }
                JsonObject json = parsed.getAsJsonObject();
                String synced = OverlayHttp.string(json, "syncedLyrics");
                String plain = OverlayHttp.string(json, "plainLyrics");
                if (!synced.isEmpty()) {
                    List<LyricLine> lines = parseByProvider(provider, synced);
                    if (!lines.isEmpty()) {
                        return new Result(lines, List.of());
                    }
                }
                if (plain.isEmpty()) {
                    String lyrics = OverlayHttp.string(json, "lyrics");
                    if (!lyrics.isEmpty()) {
                        List<LyricLine> lines = parseByProvider(provider, lyrics);
                        return lines.isEmpty() ? null : new Result(lines, List.of());
                    }
                    return null;
                }
                return new Result(List.of(), Arrays.asList(plain.split("\\r?\\n")));
            }

            List<LyricLine> lines = parseByProvider(provider, trimmed);
            return lines.isEmpty() ? null : new Result(lines, List.of());
        } catch (Exception exception) {
            if (debug) {
                cc.aerial.client.utility.ChatUtility.print("Lyrics endpoint provider error: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            return null;
        }
    }

    private static List<LyricLine> parseByProvider(String provider, String body) {
        if (body == null) {
            return List.of();
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return switch (provider == null ? "" : provider) {
            case "Custom" -> parseLyrics(trimmed);
            case "TTML (Apple-style)" -> isTTML(trimmed) ? parseTTML(trimmed) : List.of();
            case "Enhanced LRC (word-timed)" -> parseLRC(trimmed, Integer.MAX_VALUE);
            default -> parseLyrics(trimmed);
        };
    }

    private static List<LyricLine> parseLyrics(String text) {
        return isTTML(text) ? parseTTML(text) : parseLRC(text, Integer.MAX_VALUE);
    }

    private static String fetchRaw(String url, String headerLine, boolean debug, String logLabel) {
        java.util.Map<String, String> headers = null;
        if (headerLine != null && !headerLine.trim().isEmpty()) {
            String trimmed = headerLine.trim();
            int colon = trimmed.indexOf(':');
            if (colon > 0 && colon + 1 < trimmed.length()) {
                String key = trimmed.substring(0, colon).trim();
                String value = trimmed.substring(colon + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    headers = java.util.Map.of(key, value);
                }
            }
        }
        OverlayHttp.Response response = OverlayHttp.get(url, headers);
        if (response.code() == 0 && response.body().isEmpty()) {
            if (debug) {
                cc.aerial.client.utility.ChatUtility.print(logLabel + " connection issue");
            }
            return null;
        }
        return response.body();
    }

    private static JsonObject lrclibExact(String title, String artist, int durationMs, boolean debug) {
        for (String variant : titleVariants(title)) {
            try {
                String url = "https://lrclib.net/api/get?track_name=" + OverlayHttp.encode(variant)
                        + "&artist_name=" + OverlayHttp.encode(artist)
                        + (durationMs > 0 ? "&duration=" + Math.max(1, Math.round(durationMs / 1000.0f)) : "");
                OverlayHttp.Response response = OverlayHttp.get(url, null);
                if (response.code() == 200 && hasLyrics(response.json())) {
                    return response.json();
                }
            } catch (Exception exception) {
                if (debug) {
                    cc.aerial.client.utility.ChatUtility.print("LRCLIB exact lookup failed: " + exception.getClass().getSimpleName());
                }
            }
        }
        return null;
    }

    private static JsonObject lrclibSearch(String title, String artist, int durationMs, boolean debug) {
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;
        for (String url : searchUrls(title, artist)) {
            try {
                OverlayHttp.Response response = OverlayHttp.get(url, null);
                if (response.code() != 200) {
                    continue;
                }
                JsonElement parsed = JsonParser.parseString(response.body());
                if (!parsed.isJsonArray()) {
                    continue;
                }
                for (JsonElement element : parsed.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject candidate = element.getAsJsonObject();
                    if (!hasLyrics(candidate)) {
                        continue;
                    }
                    double score = score(candidate, title, artist, durationMs);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            } catch (Exception exception) {
                if (debug) {
                    cc.aerial.client.utility.ChatUtility.print("LRCLIB search failed: " + exception.getClass().getSimpleName());
                }
            }
        }
        return best;
    }

    private static List<String> searchUrls(String title, String artist) {
        List<String> urls = new ArrayList<>();
        for (String variant : titleVariants(title)) {
            urls.add("https://lrclib.net/api/search?track_name=" + OverlayHttp.encode(variant)
                    + "&artist_name=" + OverlayHttp.encode(artist));
        }
        urls.add("https://lrclib.net/api/search?q=" + OverlayHttp.encode((title == null ? "" : title) + " " + (artist == null ? "" : artist)));
        return urls;
    }

    private static List<String> titleVariants(String title) {
        List<String> variants = new ArrayList<>();
        String base = title == null ? "" : title.trim();
        addUnique(variants, base);
        String stripped = base
                .replaceAll("(?i)\\s*-\\s*(remaster(?:ed)?|\\d{4}\\s*remaster(?:ed)?|radio edit|single version|album version|explicit|clean|mono|stereo|live|sped up|slowed|nightcore).*$", "")
                .replaceAll("(?i)\\s*\\((?:[^)]*(remaster|remastered|radio edit|single version|album version|explicit|clean|mono|stereo|live|sped up|slowed|nightcore|feat\\.?|ft\\.)[^)]*)\\)", "")
                .replaceAll("(?i)\\s*\\[(?:[^]]*(remaster|remastered|radio edit|single version|album version|explicit|clean|mono|stereo|live|sped up|slowed|nightcore|feat\\.?|ft\\.)[^]]*)]", "")
                .trim();
        addUnique(variants, stripped);
        return variants;
    }

    private static void addUnique(List<String> list, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String existing : list) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return;
            }
        }
        list.add(trimmed);
    }

    private static boolean hasLyrics(JsonObject json) {
        return json != null && (!OverlayHttp.string(json, "syncedLyrics").isEmpty() || !OverlayHttp.string(json, "plainLyrics").isEmpty());
    }

    private static double score(JsonObject json, String title, String artist, int durationMs) {
        double score = 0.0;
        String normTitle = normalize(title);
        String normArtist = normalize(artist);
        String candidateTitle = normalize(OverlayHttp.string(json, "trackName").isEmpty()
                ? OverlayHttp.string(json, "name") : OverlayHttp.string(json, "trackName"));
        String candidateArtist = normalize(OverlayHttp.string(json, "artistName"));

        if (!candidateTitle.equals(normTitle)) {
            score += !candidateTitle.contains(normTitle) && !normTitle.contains(candidateTitle) ? 20.0 : 6.0;
        }
        if (!candidateArtist.equals(normArtist)) {
            score += !candidateArtist.contains(normArtist) && !normArtist.contains(candidateArtist) ? 12.0 : 4.0;
        }
        if (durationMs > 0 && json.has("duration")) {
            double expected = durationMs / 1000.0;
            double actual = json.get("duration").isJsonPrimitive() ? json.get("duration").getAsDouble() : expected;
            score += Math.min(30.0, Math.abs(actual - expected) * 0.35);
        }
        if (!OverlayHttp.string(json, "syncedLyrics").isEmpty()) {
            score -= 2.0;
        }
        return score;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("(?i)\\b(feat|ft)\\.?\\b.*", "")
                .replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]");

    private static List<LyricLine> parseLRC(String text, int durationMs) {
        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : text.split("\\r?\\n")) {
            Matcher matcher = LRC_TIMESTAMP.matcher(rawLine);
            String stripped = rawLine.replaceAll("\\[[^\\]]+]", "");
            List<Integer> timestamps = new ArrayList<>();
            while (matcher.find()) {
                int minutes = parseIntSafe(matcher.group(1), 0);
                int seconds = parseIntSafe(matcher.group(2), 0);
                int millis = 0;
                if (matcher.group(3) != null) {
                    String fraction = matcher.group(3);
                    millis = fraction.length() == 2 ? parseIntSafe(fraction, 0) * 10 : parseIntSafe(fraction, 0);
                }
                timestamps.add(minutes * 60000 + seconds * 1000 + millis);
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            boolean hasWordSpans = stripped.contains("<") && stripped.contains(">");
            if (hasWordSpans) {
                ParsedInlineLine parsed = parseInlineWordSpans(stripped.trim(), timestamps.get(0));
                for (int start : timestamps) {
                    lines.add(new LyricLine(start, parsed.text, copyWords(parsed.words), true));
                }
            } else {
                String plain = stripped.trim();
                for (int start : timestamps) {
                    lines.add(new LyricLine(start, plain, new ArrayList<>(), false));
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            int nextStart = i + 1 < lines.size() ? lines.get(i + 1).startTime : durationMs;
            if (!line.words.isEmpty()) {
                for (int w = 0; w < line.words.size(); w++) {
                    line.words.get(w).endTime = w + 1 < line.words.size()
                            ? line.words.get(w + 1).startTime : nextStart;
                }
            } else {
                line.words = estimateWordTimings(line.text, line.startTime, nextStart);
            }
        }

        lines.sort(Comparator.comparingInt(l -> l.startTime));
        return lines;
    }

    private record ParsedInlineLine(String text, List<LyricWord> words) {
    }

    private static ParsedInlineLine parseInlineWordSpans(String line, int fallbackTime) {
        List<LyricWord> words = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int cursor = 0;
        int time = fallbackTime;
        boolean sawTimestamp = false;

        while (cursor < line.length()) {
            char c = line.charAt(cursor);
            if (c == '<') {
                int close = line.indexOf('>', cursor);
                if (close > cursor) {
                    if (current.length() > 0) {
                        String word = current.toString();
                        words.add(new LyricWord(word, time));
                        fullText.append(word);
                        current.setLength(0);
                    }
                    Integer parsed = parseWordTimestamp(line.substring(cursor + 1, close));
                    if (parsed != null) {
                        time = parsed;
                        sawTimestamp = true;
                    }
                    cursor = close + 1;
                    continue;
                }
            }
            current.append(c);
            cursor++;
        }
        if (current.length() > 0) {
            String word = current.toString();
            words.add(new LyricWord(word, time));
            fullText.append(word);
        }
        words.removeIf(w -> w.text == null || w.text.isEmpty());
        if (!sawTimestamp) {
            words = new ArrayList<>();
        }
        return new ParsedInlineLine(fullText.toString(), words);
    }

    private static List<LyricWord> copyWords(List<LyricWord> words) {
        List<LyricWord> copy = new ArrayList<>();
        for (LyricWord word : words) {
            copy.add(new LyricWord(word.text, word.startTime));
        }
        return copy;
    }

    private static Integer parseWordTimestamp(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) {
                return null;
            }
            int minutes = Integer.parseInt(parts[0]);
            String[] secondParts = parts[1].split("\\.");
            int seconds = Integer.parseInt(secondParts[0]);
            int millis = 0;
            if (secondParts.length > 1) {
                String fraction = secondParts[1];
                millis = fraction.length() == 2 ? Integer.parseInt(fraction) * 10 : Integer.parseInt(fraction);
            }
            return minutes * 60000 + seconds * 1000 + millis;
        } catch (Exception exception) {
            return null;
        }
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static final Pattern TTML_P = Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>");
    private static final Pattern TTML_SPAN = Pattern.compile("(?is)<span\\b([^>]*)>(.*?)</span>");

    private static boolean isTTML(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("<") && (trimmed.contains("<tt") || trimmed.contains("http://www.w3.org/ns/ttml"));
    }

    private static List<LyricLine> parseTTML(String text) {
        List<LyricLine> lines = new ArrayList<>();
        Matcher pMatcher = TTML_P.matcher(text);
        while (pMatcher.find()) {
            String attributes = pMatcher.group(1);
            String inner = pMatcher.group(2);
            Integer begin = parseTimestamp(attribute(attributes, "begin"));
            Integer end = parseTimestamp(attribute(attributes, "end"));
            if (begin == null) {
                continue;
            }

            List<LyricWord> words = new ArrayList<>();
            boolean hasWordTiming = false;
            Matcher spanMatcher = TTML_SPAN.matcher(inner);
            StringBuilder fullText = new StringBuilder();
            while (spanMatcher.find()) {
                String spanAttributes = spanMatcher.group(1);
                String spanText = stripTags(unescapeHtml(spanMatcher.group(2)));
                Integer spanBegin = parseTimestamp(attribute(spanAttributes, "begin"));
                Integer spanEnd = parseTimestamp(attribute(spanAttributes, "end"));
                if (spanText == null) {
                    spanText = "";
                }
                if (fullText.length() > 0) {
                    char last = fullText.charAt(fullText.length() - 1);
                    if (!Character.isWhitespace(last) && !spanText.isEmpty() && !Character.isWhitespace(spanText.charAt(0))) {
                        fullText.append(' ');
                    }
                }
                fullText.append(spanText);
                if (spanBegin != null) {
                    hasWordTiming = true;
                    LyricWord word = new LyricWord(spanText, spanBegin);
                    word.endTime = spanEnd != null ? spanEnd : spanBegin;
                    words.add(word);
                }
            }

            String lineText = fullText.length() > 0 ? fullText.toString().trim() : stripTags(unescapeHtml(inner)).trim();

            if (hasWordTiming && !words.isEmpty()) {
                for (int i = 0; i < words.size(); i++) {
                    LyricWord word = words.get(i);
                    if (i + 1 < words.size()) {
                        word.endTime = Math.max(word.startTime, Math.min(word.endTime, words.get(i + 1).startTime));
                        if (word.endTime == word.startTime) {
                            word.endTime = words.get(i + 1).startTime;
                        }
                    } else if (end != null) {
                        word.endTime = Math.max(word.startTime, end);
                    }
                }
            }
            lines.add(new LyricLine(begin, lineText, words, hasWordTiming));
        }

        lines.sort(Comparator.comparingInt(l -> l.startTime));
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            int nextStart = i + 1 < lines.size() ? lines.get(i + 1).startTime : Integer.MAX_VALUE;
            if (line.words.isEmpty()) {
                line.words = estimateWordTimings(line.text, line.startTime, nextStart);
            }
        }
        return lines;
    }

    private static final java.util.Map<String, Pattern> ATTRIBUTE_PATTERNS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static String attribute(String tagAttributes, String name) {
        if (tagAttributes == null) {
            return null;
        }
        Pattern pattern = ATTRIBUTE_PATTERNS.computeIfAbsent(name,
                key -> Pattern.compile("(?i)\\b" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\""));
        Matcher matcher = pattern.matcher(tagAttributes);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripTags(String text) {
        return text == null ? "" : text.replaceAll("(?is)<[^>]+>", "");
    }

    private static String unescapeHtml(String text) {
        return text == null ? "" : text.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static Integer parseTimestamp(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (trimmed.endsWith("s")) {
                double seconds = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
                return (int) Math.round(seconds * 1000.0);
            }
            String[] parts = trimmed.split(":");
            double totalSeconds;
            if (parts.length == 3) {
                totalSeconds = Double.parseDouble(parts[2]) + Integer.parseInt(parts[1]) * 60.0 + Integer.parseInt(parts[0]) * 3600.0;
            } else if (parts.length == 2) {
                totalSeconds = Double.parseDouble(parts[1]) + Integer.parseInt(parts[0]) * 60.0;
            } else {
                totalSeconds = Double.parseDouble(parts[0]);
            }
            return (int) Math.round(totalSeconds * 1000.0);
        } catch (Exception exception) {
            return null;
        }
    }

    static List<LyricWord> estimateWordTimings(String text, int start, int end) {
        List<LyricWord> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        int totalMs = Math.max(0, end - start);
        if (totalMs == 0) {
            LyricWord word = new LyricWord(text, start);
            word.endTime = start;
            result.add(word);
            return result;
        }

        List<String> tokens = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int lastKind = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean whitespace = Character.isWhitespace(c);
            boolean wordChar = !whitespace && (Character.isLetterOrDigit(c) || c == '\'' || c == '\u2019');
            int kind = whitespace ? 0 : (wordChar ? 1 : 2);
            if (kind != lastKind && lastKind != -1) {
                tokens.add(current.toString());
                kinds.add(lastKind);
                current.setLength(0);
            }
            current.append(c);
            lastKind = kind;
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
            kinds.add(lastKind);
        }
        if (tokens.isEmpty()) {
            LyricWord word = new LyricWord(text, start);
            word.endTime = end;
            result.add(word);
            return result;
        }

        int lastWordToken = -1;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (kinds.get(i) == 1) {
                lastWordToken = i;
                break;
            }
        }
        if (lastWordToken < 0) {
            LyricWord word = new LyricWord(text, start);
            word.endTime = end;
            result.add(word);
            return result;
        }

        List<String> segments = new ArrayList<>(tokens.subList(0, lastWordToken));
        List<Integer> segmentKinds = new ArrayList<>(kinds.subList(0, lastWordToken));
        StringBuilder tail = new StringBuilder();
        for (int i = lastWordToken; i < tokens.size(); i++) {
            tail.append(tokens.get(i));
        }
        segments.add(tail.toString());
        segmentKinds.add(1);

        int size = segments.size();
        double[] weights = new double[size];
        double totalWeight = 0.0;
        for (int i = 0; i < size; i++) {
            String segment = segments.get(i);
            int kind = segmentKinds.get(i);
            double weight;
            if (kind == 1) {
                String lettersOnly = segment.replaceAll("[^\\p{L}\\p{M}\\p{Nd}'\u2019]+", "");
                int syllables = countSyllables(lettersOnly);
                double letterWeight = 0.0;
                for (int c = 0; c < segment.length(); c++) {
                    char ch = segment.charAt(c);
                    if (!Character.isWhitespace(ch)) {
                        letterWeight += 1.0 + (Character.isUpperCase(ch) ? 0.1 : 0.0);
                    }
                }
                weight = Math.max(1.0, 0.6 * Math.max(1, syllables) + 0.4 * Math.max(1.0, letterWeight));
                if (lettersOnly.matches(".*([aAeEiIoOuUyY])\\1{2,}.*")) {
                    weight += 0.6;
                }
                if (lettersOnly.length() >= 3 && lettersOnly.equals(lettersOnly.toUpperCase(Locale.ROOT))) {
                    weight += 0.5;
                }
            } else if (kind == 2) {
                weight = punctuationWeight(segment);
            } else {
                weight = Math.max(0.12, 0.1 * segment.length());
            }
            if (i == size - 1) {
                weight *= 1.45;
            }
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            Arrays.fill(weights, 1.0);
            totalWeight = size;
        }

        int[] minMs = new int[size];
        for (int i = 0; i < size; i++) {
            minMs[i] = switch (segmentKinds.get(i)) {
                case 1 -> 45;
                case 2 -> 60;
                default -> Math.min(40, 10 * Math.max(1, segments.get(i).length()));
            };
        }
        int lastMinFloor = Math.min(Math.max(100, (int) Math.round(0.22 * totalMs)), totalMs);
        minMs[size - 1] = Math.max(minMs[size - 1], lastMinFloor);

        double[] proportional = new double[size];
        for (int i = 0; i < size; i++) {
            proportional[i] = totalMs * (weights[i] / totalWeight);
        }

        int[] durations = new int[size];
        double[] remainders = new double[size];
        int assigned = 0;
        for (int i = 0; i < size; i++) {
            int floored = (int) Math.floor(proportional[i]);
            if (floored < minMs[i]) {
                floored = minMs[i];
            }
            durations[i] = floored;
            assigned += floored;
            remainders[i] = proportional[i] - Math.floor(proportional[i]);
        }

        if (assigned > totalMs) {
            redistributeOverflow(durations, minMs, segmentKinds, assigned - totalMs, size);
        } else if (assigned < totalMs) {
            redistributeShortfall(durations, remainders, segmentKinds, totalMs - assigned, size);
        }

        int sum = 0;
        for (int duration : durations) {
            sum += duration;
        }
        if (sum != totalMs) {
            durations[size - 1] += totalMs - sum;
        }

        int cursor = start;
        for (int i = 0; i < size; i++) {
            int segEnd = i == size - 1 ? end : Math.min(end, cursor + Math.max(1, durations[i]));
            LyricWord word = new LyricWord(segments.get(i), cursor);
            word.endTime = segEnd;
            result.add(word);
            cursor = segEnd;
        }
        return result;
    }

    private static void redistributeOverflow(int[] durations, int[] minMs, List<Integer> kinds, int overflow, int size) {
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            if (a == size - 1) return 1;
            if (b == size - 1) return -1;
            int surplusA = durations[a] - minMs[a];
            int surplusB = durations[b] - minMs[b];
            return surplusA != surplusB ? Integer.compare(surplusB, surplusA) : Integer.compare(kinds.get(a), kinds.get(b));
        });
        int remaining = overflow;
        for (int index : order) {
            while (remaining > 0 && durations[index] > minMs[index]) {
                durations[index]--;
                remaining--;
            }
            if (remaining == 0) {
                break;
            }
        }
    }

    private static void redistributeShortfall(int[] durations, double[] remainders, List<Integer> kinds, int shortfall, int size) {
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            if (a == size - 1) return -1;
            if (b == size - 1) return 1;
            int byRemainder = Double.compare(remainders[b], remainders[a]);
            return byRemainder != 0 ? byRemainder : -Integer.compare(kinds.get(a), kinds.get(b));
        });
        int remaining = shortfall;
        int cursor = 0;
        while (remaining > 0) {
            durations[order[cursor % size]]++;
            remaining--;
            cursor++;
        }
    }

    private static int countSyllables(String word) {
        if (word == null) {
            return 1;
        }
        String lettersOnly = word.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z]", "");
        if (lettersOnly.isEmpty()) {
            return 1;
        }
        String vowelGroups = lettersOnly.replaceAll("(?i)[^aeiouy]+", " ").trim();
        int count = vowelGroups.isEmpty() ? 0 : vowelGroups.split("\\s+").length;
        if (lettersOnly.endsWith("e") && count > 1) {
            count--;
        }
        return Math.max(1, count);
    }

    private static double punctuationWeight(String token) {
        int length = token.length();
        if (token.matches("\\.+")) {
            return 0.9 + 0.15 * (length - 1);
        }
        if (token.matches("[!?]+")) {
            return 0.8 + 0.1 * (length - 1);
        }
        if (token.matches("[,;:]+")) {
            return 0.55 + 0.08 * (length - 1);
        }
        if (token.matches("[\u2014\u2013-]+")) {
            return 0.55 + 0.05 * (length - 1);
        }
        return token.matches("[()\\[\\]\"\u201c\u201d]+") ? 0.35 : 0.3;
    }
}
