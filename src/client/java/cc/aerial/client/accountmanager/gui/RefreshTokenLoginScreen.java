package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.util.AuthExecutors;
import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.AccountManager;
import cc.aerial.client.accountmanager.AccountType;
import cc.aerial.client.accountmanager.RefreshTokenAuth;
import cc.aerial.client.accountmanager.util.Notification;
import cc.aerial.client.accountmanager.util.TextFormatting;
import cc.aerial.client.screen.widget.AerialTextArea;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RefreshTokenLoginScreen extends AltCardScreen {
    private static final Pattern MICROSOFT_REFRESH_TOKEN = Pattern.compile("M\\.C[A-Za-z0-9._!*$\\-]+");

    private AerialTextArea tokenField;

    private boolean busy;
    private static final String INITIAL_STATUS = "§7Enter Microsoft OAuth refresh token(s)§r";
    private ExecutorService executor;
    private CompletableFuture<Void> task;

    public RefreshTokenLoginScreen(Screen previousScreen) {
        super("Refresh Token", previousScreen);
    }

    @Override
    protected float cardWidth() {
        return 340.0f;
    }

    @Override
    protected float contentHeight() {
        return 96.0f;
    }

    private static final float FIELD_INSET = 7.0f;

    @Override
    protected void init() {
        String existing = tokenField == null ? "" : tokenField.getValue();
        tokenField = new AerialTextArea(cardFont(), 8.0f, "Paste one or more refresh tokens");
        tokenField.setValue(existing);
        tokenField.setFocused(true);
        clearActions();
        addAction("Log in", this::onLogin, () -> !busy);
        addBackAction();
        if (getStatus().isEmpty()) {
            setStatus(INITIAL_STATUS);
        }
    }

    private float fieldLeft() {
        return contentLeft() + FIELD_INSET;
    }

    private float fieldTop() {
        return contentTop() + FIELD_INSET;
    }

    private float fieldWidth() {
        return contentWidth() - FIELD_INSET * 2.0f;
    }

    private float fieldHeight() {
        return contentHeight() - FIELD_INSET * 2.0f;
    }

    @Override
    protected void drawCardContent(net.minecraft.client.gui.GuiGraphicsExtractor extractor,
                                   int mouseX, int mouseY, float partialTick) {
        drawInputFrame(extractor, contentLeft(), contentTop(), contentWidth(),
                contentHeight(), tokenField.isFocused());
        tokenField.draw(extractor, fieldLeft(), fieldTop(), fieldWidth(), fieldHeight());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (tokenField.mouseClicked(event.x(), event.y(), fieldLeft(), fieldTop(),
                fieldWidth(), fieldHeight())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inside(mouseX, mouseY, contentLeft(), contentTop(), contentWidth(), contentHeight())) {
            return tokenField.mouseScrolled(vertical, fieldHeight());
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return tokenField.charTyped((char) event.codepoint()) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return tokenField.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        AuthExecutors.shutdown(executor);
        super.onClose();
    }

    private void onLogin() {
        String input = tokenField.getValue().trim();
        if (input.isEmpty()) {
            setStatus("§cPlease enter at least one refresh token.§r");
            return;
        }
        List<String> refreshTokens = extractRefreshTokens(input);
        if (refreshTokens.isEmpty()) {
            setStatus("§cNo valid refresh tokens found.§r");
            return;
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(3, AuthExecutors.daemonFactory("Aerial Refresh Token Login"));
        }
        setStatus("§7Processing accounts...§r");
        busy = true;

        List<CompletableFuture<Void>> loginTasks = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> successful = new ArrayList<>();

        for (String refreshToken : refreshTokens) {
            CompletableFuture<Void> current = RefreshTokenAuth.authenticate(refreshToken, executor).thenAcceptAsync(account -> {
                Optional<Account> existing = AccountManager.accounts.stream()
                        .filter(stored -> stored.getRefreshToken().equals(refreshToken) || stored.getAccessToken().equals(account.getAccessToken()))
                        .findFirst();
                if (existing.isPresent()) {
                    Account stored = existing.get();
                    stored.setRefreshToken(account.getRefreshToken());
                    stored.setAccessToken(account.getAccessToken());
                    stored.setUsername(account.getUsername());
                    stored.setType(AccountType.REFRESH);
                    stored.setUuid(account.getUuid());
                } else {
                    AccountManager.accounts.add(account);
                }
                successful.add(account.getUsername());
            }, executor).exceptionally(error -> {
                String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                failed.add("Failed (" + message + ")");
                return null;
            });
            loginTasks.add(current);
        }

        task = CompletableFuture.allOf(loginTasks.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
            AccountManager.save();
            minecraft.execute(() -> {
                String message = summary(successful.size(), failed.size());
                minecraft.setScreenAndShow(new AltScreen(previousScreen));
            });
        }, executor).exceptionally(error -> {
            minecraft.execute(() -> {
                setStatus("§cAn unexpected error occurred during batch processing.§r");
                busy = false;
            });
            return null;
        });
    }

    private static List<String> extractRefreshTokens(String input) {
        List<String> tokens = new ArrayList<>();
        String collapsed = input.trim().replaceAll("\\s+", "");
        Matcher matcher = MICROSOFT_REFRESH_TOKEN.matcher(collapsed);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 50 && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String summary(int successCount, int failCount) {
        if (successCount > 0 && failCount == 0) {
            return String.format("&aSuccessfully logged in %d account(s)!&r", successCount);
        }
        if (successCount == 0 && failCount > 0) {
            return String.format("&cFailed to log in %d account(s).&r", failCount);
        }
        return String.format("&aLogged in %d, &cfailed %d account(s).&r", successCount, failCount);
    }
}
