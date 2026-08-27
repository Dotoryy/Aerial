package cc.aerial.client.features.impl.other.spotify;

import cc.aerial.client.overlay.OverlayHttp;
import cc.aerial.client.render.AerialImage;
import cc.aerial.client.utility.ChatUtility;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SpotifyService {
    public static final SpotifyService INSTANCE = new SpotifyService();

    private static final String USER_AGENT = "Aerial/SpotifyModule";
    private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final long NO_CONTENT_GRACE_MS = 1500L;
    private static final int NO_CONTENT_LIMIT = 3;

    private SpotifyService() {
    }

    public volatile String song = "Loading...";
    public volatile String artist = "Loading...";
    public volatile String artworkUrl = "";
    public volatile int durationMs = 1;
    public volatile int progressMs = 1;
    public volatile long progressTimestamp = System.currentTimeMillis();
    public volatile AerialImage artwork;

    public volatile boolean lyricsAvailable;
    public volatile boolean syncedLyrics;
    public volatile List<LyricLine> syncedLines = List.of();
    public volatile List<String> plainLines = List.of();

    private volatile String trackId = "";
    private volatile long trackGeneration;
    private volatile String artworkForUrl = "";
    private volatile long lastPlayingTime;
    private volatile int noContentCount;

    private static final long SPOTIFY_BACKOFF_BASE_MS = 10_000L;
    private static final long SPOTIFY_BACKOFF_MAX_MS = 120_000L;
    private volatile long spotifyBackoffUntilMs;
    private volatile int consecutive429s;

    public int estimatedProgressMs() {
        long elapsed = System.currentTimeMillis() - progressTimestamp;
        int estimate = progressMs + (int) elapsed;
        if (estimate < 0) {
            estimate = 0;
        }
        if (durationMs > 0 && estimate > durationMs) {
            estimate = durationMs;
        }
        return estimate;
    }

    private volatile boolean authServerRunning;
    private volatile String accessToken = "";
    private volatile long tokenExpiredAt;

    private volatile String refreshToken = "";
    private volatile boolean refreshTokenLoaded;

    public boolean isAuthorized() {
        return !accessToken.isEmpty();
    }

    public boolean hasCredentials(String clientId, String clientSecret) {
        return clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty();
    }

    public void clearAuthorization() {
        accessToken = "";
        tokenExpiredAt = 0L;

        refreshToken = "";
        refreshTokenLoaded = true;
        try {
            java.nio.file.Files.deleteIfExists(refreshTokenFile().toPath());
        } catch (Exception ignored) {
        }
    }

    private static java.io.File refreshTokenFile() {
        return new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory,
                "aerial" + java.io.File.separator + "spotify_token.txt");
    }

    private void loadRefreshToken() {
        if (refreshTokenLoaded) {
            return;
        }
        refreshTokenLoaded = true;
        try {
            java.io.File file = refreshTokenFile();
            if (file.isFile()) {
                refreshToken = java.nio.file.Files.readString(file.toPath()).trim();
            }
        } catch (Exception ignored) {
            refreshToken = "";
        }
    }

    private void storeRefreshToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        refreshToken = token;
        refreshTokenLoaded = true;
        try {
            java.io.File file = refreshTokenFile();
            java.io.File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), token);
        } catch (Exception ignored) {
        }
    }

    private static final long AUTO_RETRY_COOLDOWN_MS = 30_000L;
    private volatile long nextAutoAttemptMs;

    public void ensureAuthorized(String clientId, String clientSecret) {
        if (authServerRunning) {
            return;
        }
        boolean needsAuth = accessToken.isEmpty() || tokenExpiredAt > 0L;
        if (!needsAuth || !hasCredentials(clientId, clientSecret)) {
            return;
        }

        loadRefreshToken();
        if (!refreshToken.isEmpty() && refreshAccessToken(clientId, clientSecret)) {
            tokenExpiredAt = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAutoAttemptMs) {
            return;
        }
        nextAutoAttemptMs = now + AUTO_RETRY_COOLDOWN_MS;
        startAuthFlow(clientId, clientSecret);
        tokenExpiredAt = 0L;
    }

    public void startAuthFlow(String clientId, String clientSecret) {
        if (authServerRunning) {
            return;
        }
        authServerRunning = true;
        Thread server = new Thread(() -> {
            try {
                runCallbackServer(clientId, clientSecret);
            } catch (IOException exception) {
                exception.printStackTrace();
            } finally {
                authServerRunning = false;
            }
        }, "SpotifyAuthServer");
        server.setDaemon(true);
        server.start();

        String authorizeUrl = "https://accounts.spotify.com/authorize?response_type=code&client_id="
                + OverlayHttp.encode(clientId)
                + "&redirect_uri=" + OverlayHttp.encode(REDIRECT_URI)
                + "&scope=" + OverlayHttp.encode("user-read-playback-state");
        if (!openBrowser(authorizeUrl)) {
            ChatUtility.print("Spotify: couldn't open a browser automatically. Open this URL yourself: " + authorizeUrl);
            authServerRunning = false;
        }
    }

    private boolean openBrowser(String url) {
        try {
            System.setProperty("java.awt.headless", "false");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return openBrowserViaOsCommand(url);
    }

    private boolean openBrowserViaOsCommand(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        try {
            ProcessBuilder builder;
            if (os.contains("win")) {
                builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                builder = new ProcessBuilder("open", url);
            } else {
                builder = new ProcessBuilder("xdg-open", url);
            }
            builder.start();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private void runCallbackServer(String clientId, String clientSecret) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8888));
            serverSocket.setSoTimeout(500);

            while (authServerRunning) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException timeout) {
                    continue;
                }

                try (Socket connection = socket;
                     java.io.BufferedReader reader = new java.io.BufferedReader(
                             new java.io.InputStreamReader(connection.getInputStream()))) {
                    connection.setSoTimeout(6000);
                    String code = null;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.startsWith("GET") && line.contains("code=")) {
                            code = line.split("code=")[1].split(" ")[0];
                            break;
                        }
                    }

                    try (var output = connection.getOutputStream()) {
                        String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 107\r\n\r\n"
                                + "<html><body><h2>Authorization successful!</h2><p>You can now re-enable the Spotify module.</p></body></html>";
                        output.write(response.getBytes());
                    } catch (SocketException ignored) {
                    }

                    if (code != null) {
                        requestAccessToken(code, clientId, clientSecret);
                        break;
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        }
    }

    private void requestAccessToken(String code, String clientId, String clientSecret) {
        try {
            String credentials = java.util.Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
            OverlayHttp.Response response = OverlayHttp.post(
                    "https://accounts.spotify.com/api/token",
                    Map.of("Authorization", "Basic " + credentials),
                    "grant_type=authorization_code&code=" + code + "&redirect_uri=" + OverlayHttp.encode(REDIRECT_URI),
                    "application/x-www-form-urlencoded");
            if (response.json() != null && response.json().has("access_token")) {
                accessToken = response.json().get("access_token").getAsString();

                if (response.json().has("refresh_token")) {
                    storeRefreshToken(response.json().get("refresh_token").getAsString());
                }
                ChatUtility.print("Spotify: authorized.");
            } else {
                ChatUtility.print("Spotify: token exchange failed (HTTP " + response.code()
                        + "). Check the Client ID/Secret in API Settings.");
            }
        } finally {
            authServerRunning = false;
        }
    }

    private boolean refreshAccessToken(String clientId, String clientSecret) {
        try {
            String credentials = java.util.Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes());
            OverlayHttp.Response response = OverlayHttp.post(
                    "https://accounts.spotify.com/api/token",
                    Map.of("Authorization", "Basic " + credentials),
                    "grant_type=refresh_token&refresh_token=" + OverlayHttp.encode(refreshToken),
                    "application/x-www-form-urlencoded");
            if (response.json() != null && response.json().has("access_token")) {
                accessToken = response.json().get("access_token").getAsString();

                if (response.json().has("refresh_token")) {
                    storeRefreshToken(response.json().get("refresh_token").getAsString());
                }
                return true;
            }

            ChatUtility.print("Spotify: stored login expired, asking for authorization again.");
            clearAuthorization();
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private volatile boolean pollerRunning;
    private Thread pollerThread;

    public void startPoller(java.util.function.Supplier<PollConfig> config) {
        if (pollerRunning) {
            return;
        }
        pollerRunning = true;
        pollerThread = new Thread(() -> {
            while (pollerRunning) {
                PollConfig cfg = config.get();
                try {
                    ensureAuthorized(cfg.clientId(), cfg.clientSecret());
                    if ("Cider".equals(cfg.musicService())) {
                        pollCider(cfg.debug());
                    } else if (isAuthorized()) {
                        pollSpotify(cfg);
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                try {
                    Thread.sleep(nextDelayMs(cfg));
                } catch (InterruptedException interrupted) {
                    break;
                }
            }
        }, "SpotifyPoller");
        pollerThread.setDaemon(true);
        pollerThread.start();
    }

    private static final long MID_TRACK_POLL_MS = 5000L;

    private static final long NEAR_END_WINDOW_MS = 3000L;

    private long nextDelayMs(PollConfig cfg) {
        long base = Math.max(500L, cfg.refreshTicks() * 50L);
        if ("Cider".equals(cfg.musicService()) || !isAuthorized()) {
            return base;
        }
        boolean confirmedTrack = lastPlayingTime != 0L && durationMs > 1
                && !"No data".equals(song) && !"Loading...".equals(song);
        if (!confirmedTrack) {
            return base;
        }
        long remaining = durationMs - estimatedProgressMs();
        if (remaining <= NEAR_END_WINDOW_MS + base) {
            return base;
        }
        return Math.max(base, MID_TRACK_POLL_MS);
    }

    public void stopPoller() {
        pollerRunning = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }
    }

    public record PollConfig(String musicService, String clientId, String clientSecret,
                              String lyricsProvider, String lyricsEndpointUrl, String lyricsEndpointHeader,
                              int refreshTicks, boolean debug) {
    }

    private void pollCider(boolean debug) {
        try {
            long startNanos = System.nanoTime();
            OverlayHttp.Response response = OverlayHttp.get(
                    "http://localhost:10767/api/v1/playback/now-playing", null);
            if (response.json() == null || !"ok".equals(OverlayHttp.string(response.json(), "status"))) {
                return;
            }
            JsonObject info = response.json().getAsJsonObject("info");
            String newSong = OverlayHttp.string(info, "name");
            String newArtist = OverlayHttp.string(info, "artistName");
            int newDuration = info.has("durationInMillis") ? info.get("durationInMillis").getAsInt() : 1;
            String artUrl = info.has("artwork") && info.get("artwork").isJsonObject()
                    ? OverlayHttp.string(info.getAsJsonObject("artwork"), "url").replace("{w}x{h}", "600x600") : "";
            double remainingSeconds = info.has("remainingTime") ? info.get("remainingTime").getAsDouble() : 0.0;
            int newProgress = (int) (newDuration - remainingSeconds * 1000.0);

            long roundTripMs = (System.nanoTime() - startNanos) / 1_000_000L;
            long compensation = Math.min(150L, Math.max(0L, roundTripMs / 2L));

            boolean trackChanged = !newSong.equals(song) || !newArtist.equals(artist);
            song = newSong;
            artist = newArtist;
            durationMs = newDuration;
            artworkUrl = artUrl;
            progressMs = newProgress;
            progressTimestamp = System.currentTimeMillis() - compensation;
            if (trackChanged) {
                long generation = ++trackGeneration;
                resetTrackAssets();
                requestLyrics(generation);
                requestArtwork(artUrl, generation);
            }
        } catch (Exception exception) {
            if (debug) {
                ChatUtility.print("Cider poll error: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }

    private void pollSpotify(PollConfig cfg) {
        if (System.currentTimeMillis() < spotifyBackoffUntilMs) {
            return;
        }
        try {
            OverlayHttp.Response response = OverlayHttp.get(
                    "https://api.spotify.com/v1/me/player?market=US",
                    Map.of("Authorization", "Bearer " + accessToken));
            long now = System.currentTimeMillis();

            if (response.code() == 429) {
                consecutive429s++;
                long backoff = Math.min(SPOTIFY_BACKOFF_BASE_MS << Math.min(consecutive429s - 1, 4), SPOTIFY_BACKOFF_MAX_MS);
                spotifyBackoffUntilMs = now + backoff;
                if (cfg.debug()) {
                    ChatUtility.print("Spotify: rate limited (429), backing off " + (backoff / 1000) + "s");
                }
                return;
            }
            consecutive429s = 0;

            if (response.code() == 200 && response.json() != null && response.json().has("item")) {
                noContentCount = 0;
                lastPlayingTime = now;
                JsonObject root = response.json();
                JsonObject item = root.getAsJsonObject("item");

                song = OverlayHttp.string(item, "name");
                StringBuilder artists = new StringBuilder();
                if (item.has("artists") && item.get("artists").isJsonArray()) {
                    for (JsonElement element : item.getAsJsonArray("artists")) {
                        if (!artists.isEmpty()) {
                            artists.append(", ");
                        }
                        artists.append(OverlayHttp.string(element.getAsJsonObject(), "name"));
                    }
                }
                artist = artists.toString();
                String artUrl = "";
                if (item.has("album") && item.getAsJsonObject("album").has("images")) {
                    JsonArray images = item.getAsJsonObject("album").getAsJsonArray("images");
                    if (!images.isEmpty()) {
                        artUrl = OverlayHttp.string(images.get(0).getAsJsonObject(), "url");
                    }
                }
                artworkUrl = artUrl;
                durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsInt() : 1;
                progressMs = root.has("progress_ms") ? root.get("progress_ms").getAsInt() : 0;
                long serverTimestamp = root.has("timestamp") ? root.get("timestamp").getAsLong() : now;
                long clockDrift = Math.max(0L, now - serverTimestamp);
                long compensation = Math.min(300L, Math.max(0L, clockDrift / 2L));
                progressTimestamp = now - compensation;

                String newTrackId = OverlayHttp.string(item, "id");
                boolean trackChanged = !newTrackId.equals(trackId);
                if (trackChanged) {
                    trackId = newTrackId;
                    long generation = ++trackGeneration;
                    resetTrackAssets();
                    requestLyrics(generation, cfg);
                    if (!artUrl.isEmpty()) {
                        requestArtwork(artUrl, generation);
                    }
                } else {
                    boolean needsLyrics = !lyricsAvailable || lineCount() == 0;
                    if (needsLyrics) {
                        requestLyrics(trackGeneration, cfg);
                    }
                    boolean needsArtwork = artwork == null && !artUrl.isEmpty();
                    if (needsArtwork && !Objects.equals(artworkForUrl, artUrl)) {
                        requestArtwork(artUrl, trackGeneration);
                    }
                }
            } else if (response.code() == 204) {
                noContentCount++;
                long sinceLastPlaying = lastPlayingTime == 0L ? Long.MAX_VALUE : now - lastPlayingTime;
                if (sinceLastPlaying >= NO_CONTENT_GRACE_MS && noContentCount >= NO_CONTENT_LIMIT) {
                    song = "No data";
                    artist = "No data";
                    artworkUrl = "";
                    progressMs = 999;
                    durationMs = 999;
                    lyricsAvailable = false;
                    syncedLines = List.of();
                    plainLines = List.of();
                    trackId = "";
                    ++trackGeneration;
                    resetTrackAssets();
                }
            } else if (response.code() == 401) {
                tokenExpiredAt = System.currentTimeMillis();
                accessToken = "";
            } else if (cfg.debug()) {
                ChatUtility.print("Spotify poll HTTP " + response.code() + ": " + response.body());
            }
        } catch (Exception exception) {
            if (cfg.debug()) {
                ChatUtility.print("Spotify poll error: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }

    private int lineCount() {
        return syncedLyrics ? syncedLines.size() : plainLines.size();
    }

    private void resetTrackAssets() {
        if (artwork != null) {
            artwork.close();
        }
        artwork = null;
        artworkForUrl = "";
    }

    private void requestLyrics(long generation) {
        requestLyrics(generation, null);
    }

    private void requestLyrics(long generation, PollConfig cfg) {
        lyricsAvailable = false;
        syncedLines = List.of();
        plainLines = List.of();
        syncedLyrics = false;
        Thread thread = new Thread(() -> {
            if (generation != trackGeneration) {
                return;
            }
            String provider = cfg == null ? "LRCLIB" : cfg.lyricsProvider();
            String endpointUrl = cfg == null ? "" : cfg.lyricsEndpointUrl();
            String endpointHeader = cfg == null ? "" : cfg.lyricsEndpointHeader();
            boolean debug = cfg != null && cfg.debug();
            SpotifyLyrics.Result result = SpotifyLyrics.fetch(song, artist, provider, endpointUrl, endpointHeader, durationMs, debug);
            if (generation != trackGeneration) {
                return;
            }
            if (!result.syncedLines().isEmpty()) {
                syncedLines = result.syncedLines();
                syncedLyrics = true;
                lyricsAvailable = true;
            } else if (!result.plainLines().isEmpty()) {
                plainLines = result.plainLines();
                syncedLyrics = false;
                lyricsAvailable = true;
            }
        }, "SpotifyLyricsFetch-" + generation);
        thread.setDaemon(true);
        thread.start();
    }

    private void requestArtwork(String url, long generation) {
        if (url == null || url.isEmpty()) {
            return;
        }
        artworkForUrl = url;
        Thread thread = new Thread(() -> {
            BufferedImage image = downloadImage(url);
            if (image != null && generation == trackGeneration) {
                artwork = AerialImage.fromImage(image);
            }
        }, "SpotifyArtworkFetch-" + generation);
        thread.setDaemon(true);
        thread.start();
    }

    private BufferedImage downloadImage(String url) {
        try {
            URL target = new URL(url);
            java.net.URLConnection connection = target.openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(6000);
            try (InputStream input = connection.getInputStream()) {
                return ImageIO.read(input);
            }
        } catch (Exception exception) {
            return null;
        }
    }
}
