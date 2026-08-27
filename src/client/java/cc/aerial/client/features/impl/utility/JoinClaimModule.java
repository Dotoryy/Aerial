package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.utility.Stopwatch;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public final class JoinClaimModule extends Module {
    public static final JoinClaimModule INSTANCE = new JoinClaimModule();

    private static final String SERVER_ID = "31531515";
    private static final String JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join";

    private final Stopwatch timer = new Stopwatch();

    private JoinClaimModule() {
        super("Join Claim", "Claims a session-server join for the account", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getUser() == null
                || mc.hasSingleplayerServer() || !timer.hasTimeElapsed(50L, true)) {
            return;
        }
        User user = mc.getUser();

        String accessToken = user.getAccessToken();
        String profileId = String.valueOf(user.getProfileId());
        CompletableFuture.runAsync(() -> claim(accessToken, profileId)).exceptionally(ex -> null);
    }

    private static void claim(String accessToken, String profileId) {
        try {
            HttpURLConnection connection = open(accessToken, profileId);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static HttpURLConnection open(String accessToken, String profileId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", profileId);
        body.addProperty("serverId", SERVER_ID);

        URL url = URI.create(JOIN_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            out.write(payload, 0, payload.length);
        }
        return connection;
    }
}
