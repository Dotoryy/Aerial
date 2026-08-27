package cc.aerial.client.accountmanager;

import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.minecraft.UserApiService;
import cc.aerial.client.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

public final class SessionManager {
    private static User launchUser;

    private SessionManager() {
    }

    public static void captureLaunchSession() {
        if (launchUser == null) {
            User current = get();
            if (current != null && current.getName() != null && !current.getName().isBlank()) {
                launchUser = current;
            }
        }
    }

    public static User getLaunchSession() {
        return launchUser;
    }

    public static boolean isUsingLaunchSession() {
        if (launchUser == null) {
            return true;
        }
        User current = get();
        if (current == null) {
            return true;
        }
        return safe(launchUser.getName()).equals(safe(current.getName()))
                && launchUser.getProfileId().equals(current.getProfileId())
                && safe(launchUser.getAccessToken()).equals(safe(current.getAccessToken()));
    }

    public static void restoreLaunchSession() {
        if (launchUser != null) {
            set(launchUser);
        }
    }

    public static User get() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null ? minecraft.getUser() : null;
    }

    public static void set(User user) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || user == null) {
            return;
        }
        MinecraftAccessor accessor = (MinecraftAccessor) minecraft;
        accessor.aerial$setUser(user);

        String token = user.getAccessToken();
        if (token == null || token.isBlank()) {
            accessor.aerial$setUserApiService(UserApiService.OFFLINE);
            accessor.aerial$setProfileKeyPairManager(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
            return;
        }

        try {
            UserApiService service = new YggdrasilAuthenticationService(minecraft.getProxy())
                    .createUserApiService(token);
            accessor.aerial$setUserApiService(service);
            accessor.aerial$setProfileKeyPairManager(
                    ProfileKeyPairManager.create(service, user, minecraft.gameDirectory.toPath()));
        } catch (Exception e) {
            accessor.aerial$setUserApiService(UserApiService.OFFLINE);
            accessor.aerial$setProfileKeyPairManager(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
        }
    }

    public static User createUser(String username, String uuid, String accessToken) {
        return new User(username, parseUuid(username, uuid), accessToken, Optional.empty(), Optional.empty());
    }

    private static UUID parseUuid(String username, String uuid) {
        if (uuid != null && !uuid.isBlank()) {
            try {
                return UUID.fromString(withDashes(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
    }

    private static String withDashes(String uuid) {
        String stripped = uuid.replace("-", "");
        if (stripped.length() != 32) {
            return uuid;
        }
        return stripped.substring(0, 8) + "-" + stripped.substring(8, 12) + "-" + stripped.substring(12, 16)
                + "-" + stripped.substring(16, 20) + "-" + stripped.substring(20);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
