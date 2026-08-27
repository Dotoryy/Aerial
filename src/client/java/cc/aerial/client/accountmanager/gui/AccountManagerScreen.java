package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.util.AuthExecutors;
import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.AccountLogin;
import cc.aerial.client.accountmanager.AccountManager;
import cc.aerial.client.accountmanager.CrackedAuth;
import cc.aerial.client.accountmanager.SessionManager;
import cc.aerial.client.accountmanager.util.Notification;
import cc.aerial.client.accountmanager.util.TextFormatting;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountManagerScreen extends Screen {
    private final Screen previousScreen;
    private static Notification notification;

    private Button loginButton;
    private Button deleteButton;
    private Button restoreButton;
    private AccountRowList accountList;
    private int selectedIndex = -1;
    private ExecutorService executor;
    private CompletableFuture<Void> task;

    public AccountManagerScreen(Screen previousScreen) {
        super(Component.literal("Aerial Account Manager"));
        this.previousScreen = previousScreen;
    }

    public AccountManagerScreen(Screen previousScreen, Notification notification) {
        this(previousScreen);
        AccountManagerScreen.notification = notification;
    }

    public static void setNotification(Notification notification) {
        AccountManagerScreen.notification = notification;
    }

    @Override
    protected void init() {
        AccountManager.load();
        SessionManager.captureLaunchSession();

        accountList = new AccountRowList(minecraft, width, height - 96, 32, 16);
        accountList.rebuild();
        addRenderableWidget(accountList);

        loginButton = addRenderableWidget(Button.builder(Component.literal("Login"), b -> onLogin())
                .pos(width / 2 - 150 - 4, height - 52).size(150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> minecraft.setScreenAndShow(new AddAccountScreen(previousScreen)))
                .pos(width / 2 + 4, height - 52).size(150, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> onDelete())
                .pos(width / 2 - 150 - 4, height - 28).size(150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreenAndShow(previousScreen))
                .pos(width / 2 + 4, height - 28).size(150, 20).build());

        if (SessionManager.getLaunchSession() != null) {
            String label = "Restore: " + SessionManager.getLaunchSession().getName();
            int restoreWidth = Math.min(220, font.width(label) + 12);
            restoreButton = addRenderableWidget(Button.builder(Component.literal(label), b -> onRestore())
                    .pos(width - restoreWidth - 6, 6).size(restoreWidth, 20).build());
        } else {
            restoreButton = null;
        }

        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = selectedIndex >= 0;
        boolean busy = task != null && !task.isDone();
        deleteButton.active = hasSelection;
        loginButton.active = hasSelection && !busy;
        if (restoreButton != null) {
            restoreButton.active = !SessionManager.isUsingLaunchSession() && (task == null || task.isDone());
        }
    }

    private void onLogin() {
        if (selectedIndex < 0 || selectedIndex >= AccountManager.accounts.size()) {
            return;
        }
        if (task != null && !task.isDone()) {
            return;
        }
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(AuthExecutors.daemonFactory("Aerial Account Manager"));
        }
        Account account = AccountManager.accounts.get(selectedIndex);
        String username = account.getUsername() == null || account.getUsername().isBlank() ? "???" : account.getUsername();

        if (account.getType() == cc.aerial.client.accountmanager.AccountType.CRACKED) {
            boolean success = CrackedAuth.login(account.getUsername());
            notification = success
                    ? new Notification(TextFormatting.translate(String.format("&aSuccessful login! (%s)&r", account.getUsername())), 5000L)
                    : new Notification(TextFormatting.translate(String.format("&cFailed to log in! (%s)&r", account.getUsername())), 5000L);
            updateButtons();
            return;
        }

        notification = new Notification(TextFormatting.translate(String.format("&7Fetching your Minecraft profile... (%s)&r", username)), -1L);
        updateButtons();
        task = AccountLogin.login(account, executor).whenComplete((ignored, error) -> minecraft.execute(this::updateButtons));
    }

    private void onDelete() {
        if (selectedIndex < 0 || selectedIndex >= AccountManager.accounts.size()) {
            return;
        }
        AccountManager.accounts.remove(selectedIndex);
        AccountManager.save();
        selectedIndex = -1;
        accountList.rebuild();
        updateButtons();
    }

    private void onRestore() {
        SessionManager.restoreLaunchSession();
        notification = new Notification(TextFormatting.translate(String.format("&aRestored launch session (%s)&r", SessionManager.get().getName())), 5000L);
        updateButtons();
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
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(font, TextFormatting.translate(String.format("&rAerial Account Manager &8(&7%s&8)&r", AccountManager.accounts.size())), width / 2, 20, -1);

        User current = SessionManager.get();
        if (current != null) {
            extractor.text(font, TextFormatting.translate(String.format("&7Username: &3%s&r", current.getName())), 3, 3, -1);
        }

        if (notification != null && !notification.isExpired()) {
            String text = notification.getMessage();
            int textWidth = font.width(text);
            extractor.fill(width / 2 - textWidth / 2 - 3, 4, width / 2 + textWidth / 2 + 3, 7 + font.lineHeight + 2, 0x64000000);
            extractor.centeredText(font, text, width / 2, 7, -1);
        }
    }

    private final class AccountRowList extends ObjectSelectionList<AccountRowList.AccountRow> {
        AccountRowList(net.minecraft.client.Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height, y0, itemHeight);
        }

        void rebuild() {
            clearEntries();
            for (Account account : AccountManager.accounts) {
                addEntry(new AccountRow(account));
            }
        }

        final class AccountRow extends Entry<AccountRow> {
            private final Account account;

            AccountRow(Account account) {
                this.account = account;
            }

            @Override
            public Component getNarration() {
                return Component.literal(account.getUsername() == null ? "" : account.getUsername());
            }

            @Override
            public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
                String username = account.getUsername();
                if (username == null || username.isBlank()) {
                    username = "§7§l?";
                }
                User current = SessionManager.get();
                if (current != null && username.equals(current.getName())) {
                    username = "§a§l" + username;
                }
                String suffix = switch (account.getType()) {
                    case CRACKED -> " &7(Cracked)";
                    case COOKIE -> " &7(Cookie)";
                    case REFRESH -> " &7(Refresh)";
                    case TOKEN -> " &7(Token)";
                    default -> " &7(Premium)";
                };
                String translatedUsername = TextFormatting.translate("&r" + username);
                String translatedSuffix = TextFormatting.translate(suffix);
                extractor.text(font, translatedUsername, getContentX() + 2, getContentY() + 2, -1);
                extractor.text(font, translatedSuffix, getContentX() + 2 + font.width(translatedUsername), getContentY() + 2, -1);

                long currentTime = System.currentTimeMillis();
                long unbanTime = account.getUnban();
                String unban;
                if (unbanTime < 0L) {
                    unban = "&4&l⚠";
                } else if (unbanTime <= currentTime) {
                    unban = "&2&l✔";
                } else {
                    long diff = unbanTime - currentTime;
                    long s = diff / 1000L % 60L;
                    long m = diff / 60000L % 60L;
                    long h = diff / 3600000L % 24L;
                    long d = diff / 86400000L;
                    StringBuilder sb = new StringBuilder();
                    if (d > 0) sb.append(d).append('d');
                    if (h > 0) sb.append(' ').append(h).append('h');
                    if (m > 0) sb.append(' ').append(m).append('m');
                    if (s > 0) sb.append(' ').append(s).append('s');
                    unban = sb.toString().trim() + " &c&l⚠";
                }
                unban = TextFormatting.translate("&r" + unban + "&r");
                extractor.text(font, unban, getContentRight() - 5 - font.width(unban), getContentY() + 2, -1);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                selectedIndex = children().indexOf(this);
                setSelected(this);
                updateButtons();
                if (doubleClick) {
                    onLogin();
                }
                return true;
            }
        }
    }
}
