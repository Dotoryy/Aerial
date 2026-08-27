package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.CookieAuth;
import cc.aerial.client.accountmanager.util.ModernFileChooser;
import cc.aerial.client.accountmanager.util.TextFormatting;
import net.minecraft.client.gui.screens.Screen;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class CookieAuthScreen extends AltCardScreen {
    private CompletableFuture<Boolean> task;
    private boolean openButtonEnabled = true;

    public CookieAuthScreen(Screen previousScreen) {
        super("Cookie", previousScreen);
    }

    @Override
    protected float contentHeight() {
        return 0.0f;
    }

    @Override
    protected void init() {
        clearActions();
        addAction("Open file", this::onOpenFile, () -> openButtonEnabled);
        addBackAction();
        setStatus("&fSelect a cookie file to authenticate&r");
    }

    private void setStatusAsync(String status) {
        minecraft.execute(() -> setStatus(status));
    }

    private void onOpenFile() {
        setStatusAsync("&aOpening file picker...&r");
        ModernFileChooser.showOpenDialog("Select Cookie File", new File(System.getProperty("user.home"), "Downloads"),
                "Cookie / text files (*.txt, *.json, *.cookies)", new String[]{"txt", "json", "cookies"},
                selectedFile -> {
                    openButtonEnabled = false;
                    setStatusAsync("&fReading cookie file...&r");
                    task = CookieAuth.addAccountFromCookieFile(selectedFile, this::setStatusAsync);
                    task.whenComplete((success, error) -> minecraft.execute(() -> {
                        openButtonEnabled = true;
                        if (Boolean.TRUE.equals(success)) {
                            minecraft.setScreenAndShow(new AltScreen(previousScreen));
                        } else if (error != null) {
                            setStatusAsync("&cAuthentication failed: " + error.getMessage() + "&r");
                        }
                    }));
                },
                () -> setStatusAsync("&eFile selection canceled.&r"));
    }

    @Override
    public void onClose() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        super.onClose();
    }
}
