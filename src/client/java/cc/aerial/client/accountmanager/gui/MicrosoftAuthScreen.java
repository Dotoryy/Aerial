package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.util.AuthExecutors;
import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.AccountManager;
import cc.aerial.client.accountmanager.AccountType;
import cc.aerial.client.accountmanager.MicrosoftAuth;
import cc.aerial.client.accountmanager.SessionManager;
import cc.aerial.client.accountmanager.util.Notification;
import cc.aerial.client.accountmanager.util.SystemUtils;
import cc.aerial.client.accountmanager.util.TextFormatting;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class MicrosoftAuthScreen extends AltCardScreen {
    private static final long DOT_ANIMATION_INTERVAL = 200L;

    private final String state;

    private boolean linksEnabled = true;
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private boolean success;
    private long lastDotUpdateTime;
    private int dotCount;

    @Override
    protected float contentHeight() {
        return 12.0f;
    }

    public MicrosoftAuthScreen(Screen previousScreen) {
        super("Microsoft", previousScreen);
        this.state = randomState();
        this.lastDotUpdateTime = System.currentTimeMillis();
    }

    private static String randomState() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    @Override
    protected void init() {
        clearActions();
        addAction("Open link", this::onOpenLink, () -> linksEnabled);
        addAction("Copy link", this::onCopyLink, () -> linksEnabled);
        addBackAction();

        if (task == null) {
            setStatus("&fWaiting for login&r");
            executor = Executors.newSingleThreadExecutor(AuthExecutors.daemonFactory("Aerial Microsoft Auth"));
            AtomicReference<String> refreshTokenRef = new AtomicReference<>("");
            AtomicReference<String> accessTokenRef = new AtomicReference<>("");
            task = MicrosoftAuth.acquireMSAuthCode(state, executor).thenComposeAsync(msAuthCode -> {
                linksEnabled = false;
                setStatus("&fAcquiring Microsoft access tokens&r");
                return MicrosoftAuth.acquireMSAccessTokens(msAuthCode, executor);
            }, executor).thenComposeAsync(msAccessTokens -> {
                setStatus("&fAcquiring Xbox access token.&r");
                refreshTokenRef.set(msAccessTokens.get("refresh_token"));
                return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), executor);
            }, executor).thenComposeAsync(xboxAccessToken -> {
                setStatus("&fAcquiring Xbox XSTS token&r");
                return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor);
            }, executor).thenComposeAsync(xboxXstsData -> {
                setStatus("&fAcquiring Minecraft access token&r");
                return MicrosoftAuth.acquireMCAccessToken(xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor);
            }, executor).thenComposeAsync(mcToken -> {
                setStatus("&fFetching your Minecraft profile&r");
                accessTokenRef.set(mcToken);
                return MicrosoftAuth.login(mcToken, executor);
            }, executor).thenAccept(user -> {
                setStatus(null);
                Account acc = new Account(refreshTokenRef.get(), accessTokenRef.get(), user.getName(), user.getProfileId().toString(), 0L, AccountType.MICROSOFT);
                for (Account existing : AccountManager.accounts) {
                    if (acc.getUsername().equals(existing.getUsername())) {
                        acc.setUnban(existing.getUnban());
                        break;
                    }
                }
                AccountManager.accounts.add(acc);
                AccountManager.save();
                SessionManager.set(user);
                success = true;
            }).exceptionally(error -> {
                linksEnabled = true;

                Throwable rootCause = error.getCause();
                String reason = rootCause != null && rootCause.getMessage() != null
                        ? rootCause.getMessage() : "unknown error";
                setStatus("&cLogin failed: " + reason + "&r");
                return null;
            });
        }
    }

    private void onOpenLink() {
        SystemUtils.openWebLink(MicrosoftAuth.getMSAuthLink(state));
        setStatus("&fPlease complete the login in your browser&r");
        lastDotUpdateTime = System.currentTimeMillis();
        dotCount = 0;
    }

    private void onCopyLink() {
        URI url = MicrosoftAuth.getMSAuthLink(state);
        SystemUtils.setClipboard(url.toString());
        setStatus("&aLogin link copied!&r");
        dotCount = 0;
    }

    @Override
    public void onClose() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        AuthExecutors.shutdown(executor);
        super.onClose();
    }

    @Override
    public void tick() {
        if (success) {
            cc.aerial.client.notification.NotificationManager.INSTANCE
                    .builder(cc.aerial.client.notification.NotificationType.SUCCESS)
                    .title("Logged in")
                    .description(SessionManager.get().getName())
                    .duration(4000)
                    .buildAndPublish();
            minecraft.setScreenAndShow(new AltScreen(previousScreen));
            success = false;
            return;
        }
    }
}
