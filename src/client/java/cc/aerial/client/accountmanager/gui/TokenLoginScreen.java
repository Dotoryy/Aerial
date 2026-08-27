package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.util.AuthExecutors;
import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.AccountManager;
import cc.aerial.client.accountmanager.MicrosoftAuth;
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

public final class TokenLoginScreen extends AltCardScreen {
    private static final Pattern JWT_TOKEN_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*");
    private static final Pattern TOKEN_LIKE_PATTERN = Pattern.compile("[a-zA-Z0-9\\-_]{20,}(?:\\.[a-zA-Z0-9\\-_]+){2,}|[a-zA-Z0-9\\-_]{100,}");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private AerialTextArea tokenField;

    private boolean busy;
    private static final String INITIAL_STATUS = "§7Enter your Minecraft Access Token(s)§r";
    private ExecutorService executor;
    private CompletableFuture<Void> task;

    public TokenLoginScreen(Screen previousScreen) {
        super("Access Token", previousScreen);
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
        tokenField = new AerialTextArea(cardFont(), 8.0f, "Paste one or more tokens");
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
            setStatus("§cPlease enter at least one account.§r");
            return;
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(5, AuthExecutors.daemonFactory("Aerial Token Login"));
        }
        setStatus("§7Processing accounts...§r");
        busy = true;

        List<CompletableFuture<Void>> loginTasks = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> successful = new ArrayList<>();
        List<String> processed = new ArrayList<>();

        Matcher jwtMatcher = JWT_TOKEN_PATTERN.matcher(input);
        while (jwtMatcher.find()) {
            String token = jwtMatcher.group();
            if (!processed.contains(token)) {
                processed.add(token);
                processSimpleToken(token, loginTasks, failed, successful);
            }
        }

        if (processed.isEmpty()) {
            for (String line : input.split("[\\r\\n]+")) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.contains("|")) {
                    String[] parts = line.split("\\|");
                    String token = parts.length > 0 ? parts[0].trim() : "";
                    String username = parts.length > 1 ? parts[1].trim() : null;
                    String uuid = parts.length > 2 ? parts[2].trim() : null;
                    if (!token.isEmpty() && token.length() >= 20) {
                        processed.add(token);
                        processAccountEntry(token, username, uuid, loginTasks, failed, successful);
                    }
                } else if (TOKEN_LIKE_PATTERN.matcher(line).matches() && !processed.contains(line)) {
                    processed.add(line);
                    processSimpleToken(line, loginTasks, failed, successful);
                }
            }
        }

        if (loginTasks.isEmpty()) {
            setStatus("§cNo valid tokens found.§r");
            busy = false;
            return;
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

    private void processSimpleToken(String token, List<CompletableFuture<Void>> tasks, List<String> failed, List<String> successful) {
        processAccountEntry(token, null, null, tasks, failed, successful);
    }

    private void processAccountEntry(String token, String username, String uuid, List<CompletableFuture<Void>> tasks, List<String> failed, List<String> successful) {
        CompletableFuture<net.minecraft.client.User> loginFuture = username != null && !username.isBlank() && uuid != null && UUID_PATTERN.matcher(uuid).matches()
                ? MicrosoftAuth.login(token, username, uuid, executor)
                : MicrosoftAuth.login(token, executor);
        CompletableFuture<Void> currentTask = loginFuture.thenAcceptAsync(user -> {
            Optional<Account> existing = AccountManager.accounts.stream().filter(acc -> acc.getAccessToken().equals(token)).findFirst();
            if (existing.isPresent()) {
                existing.get().setUsername(user.getName());
                existing.get().setUuid(user.getProfileId().toString());
            } else {
                AccountManager.accounts.add(new Account(user.getName(), token, user.getProfileId().toString()));
            }
            successful.add(user.getName());
        }, executor).exceptionally(error -> {
            String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
            failed.add("Failed (" + message + ")");
            return null;
        });
        tasks.add(currentTask);
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
