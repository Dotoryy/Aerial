package cc.aerial.client.accountmanager;

import net.minecraft.client.User;

public final class CrackedAuth {
    private CrackedAuth() {
    }

    public static boolean login(String username) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("[CrackedAuth] username cannot be null or empty!");
            return false;
        }
        AccountManager.addCrackedAccount(username);
        User user = SessionManager.createUser(username, null, "accessToken");
        SessionManager.set(user);
        System.out.println("[CrackedAuth] successfully logged in as: " + username);
        return true;
    }
}
