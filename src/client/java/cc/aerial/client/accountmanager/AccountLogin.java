package cc.aerial.client.accountmanager;

import cc.aerial.client.accountmanager.gui.AccountManagerScreen;
import cc.aerial.client.accountmanager.util.Notification;
import cc.aerial.client.accountmanager.util.TextFormatting;
import net.minecraft.client.User;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AccountLogin {
    private AccountLogin() {
    }

    public static CompletableFuture<Void> login(Account account, Executor executor) {
        String username = account.getUsername() == null || account.getUsername().isBlank() ? "???" : account.getUsername();
        AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Fetching your Minecraft profile... (%s)&r", username)), -1L));
        return MicrosoftAuth.login(account.getAccessToken(), executor).handle((user, error) -> {
            if (user != null) {
                applySession(account, user);
                AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&aSuccessful login! (%s)&r", account.getUsername())), 5000L));
                return CompletableFuture.<Void>completedFuture(null);
            }
            return fallbackLogin(account, executor, username);
        }).thenComposeAsync(future -> future, executor).exceptionally(error -> {
            String message = rootMessage(error);
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&c%s (%s)&r", message, username)), 5000L));
            return null;
        });
    }

    private static CompletableFuture<Void> fallbackLogin(Account account, Executor executor, String username) {
        AccountType type = account.getType();
        if (type == AccountType.PREMIUM) {
            type = AccountType.MICROSOFT;
        }
        return switch (type) {
            case COOKIE -> {
                if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
                    yield failed("No saved cookies for this account");
                }
                AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Re-authenticating with cookies... (%s)&r", username)), -1L));
                yield CookieAuth.loginWithStoredCookies(account.getRefreshToken(), executor).thenAccept(refreshed -> mergeRefreshedAccount(account, refreshed));
            }
            case REFRESH -> {
                if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
                    yield failed("No saved refresh token for this account");
                }
                AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Refreshing Microsoft tokens... (%s)&r", username)), -1L));
                yield RefreshTokenAuth.authenticate(account.getRefreshToken(), executor).thenAccept(refreshed -> mergeRefreshedAccount(account, refreshed));
            }
            case MICROSOFT -> {
                if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
                    yield failed("No saved Microsoft refresh token for this account");
                }
                yield microsoftOAuthFallback(account, executor, username);
            }
            case TOKEN -> failed("Minecraft access token expired and cannot be refreshed");
            default -> failed("Unsupported account type for login");
        };
    }

    private static CompletableFuture<Void> microsoftOAuthFallback(Account account, Executor executor, String username) {
        AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Refreshing Microsoft access tokens... (%s)&r", username)), -1L));
        return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), executor).thenComposeAsync(msAccessTokens -> {
            account.setRefreshToken(msAccessTokens.get("refresh_token"));
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Acquiring Xbox access token... (%s)&r", username)), -1L));
            return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), executor);
        }, executor).thenComposeAsync(xboxAccessToken -> {
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Acquiring Xbox XSTS token... (%s)&r", username)), -1L));
            return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor);
        }, executor).thenComposeAsync(xboxXstsData -> {
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Acquiring Minecraft access token... (%s)&r", username)), -1L));
            return MicrosoftAuth.acquireMCAccessToken(xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor);
        }, executor).thenComposeAsync(mcToken -> {
            account.setAccessToken(mcToken);
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&7Fetching your Minecraft profile... (%s)&r", username)), -1L));
            return MicrosoftAuth.login(mcToken, executor);
        }, executor).thenAccept(user -> {
            applySession(account, user);
            AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&aSuccessful login! (%s)&r", account.getUsername())), 5000L));
        });
    }

    private static void mergeRefreshedAccount(Account account, Account refreshed) {
        account.setRefreshToken(refreshed.getRefreshToken());
        account.setAccessToken(refreshed.getAccessToken());
        account.setUsername(refreshed.getUsername());
        account.setUuid(refreshed.getUuid());
        account.setType(refreshed.getType());
        User user = SessionManager.createUser(refreshed.getUsername(), refreshed.getUuid(), refreshed.getAccessToken());
        SessionManager.set(user);
        AccountManager.save();
        AccountManagerScreen.setNotification(new Notification(TextFormatting.translate(String.format("&aSuccessful login! (%s)&r", account.getUsername())), 5000L));
    }

    private static void applySession(Account account, User user) {
        account.setUsername(user.getName());
        account.setUuid(user.getProfileId().toString());
        account.setAccessToken(user.getAccessToken());
        SessionManager.set(user);
        AccountManager.save();
    }

    private static CompletableFuture<Void> failed(String message) {
        return CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException(message);
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        String best = error.getMessage();
        int depth = 0;
        while (current.getCause() != null && current.getCause() != current && depth < 8) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                best = current.getMessage();
            }
            depth++;
        }
        return best == null ? error.getClass().getSimpleName() : best;
    }
}
