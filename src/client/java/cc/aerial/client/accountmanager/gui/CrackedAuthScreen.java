package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.CrackedAuth;
import cc.aerial.client.accountmanager.util.UsernameGenerator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class CrackedAuthScreen extends Screen {
    private final Screen previousScreen;
    private EditBox usernameField;

    public CrackedAuthScreen(Screen previousScreen) {
        super(Component.literal("Cracked Authentication"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        usernameField = new EditBox(font, width / 2 - 100, height / 2 - 30, 200, 20, Component.literal("Username"));
        usernameField.setMaxLength(16);
        addRenderableWidget(usernameField);
        addRenderableWidget(Button.builder(Component.literal("Login"), b -> handleLogin())
                .pos(width / 2 - 100, height / 2).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Generate Random"), b -> handleGenerateRandom())
                .pos(width / 2 - 100, height / 2 + 25).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreenAndShow(previousScreen))
                .pos(width / 2 - 100, height / 2 + 50).size(200, 20).build());
        setInitialFocus(usernameField);
    }

    private void handleLogin() {
        String username = usernameField.getValue().trim();
        if (username.isEmpty()) {
            return;
        }
        if (CrackedAuth.login(username)) {
            minecraft.setScreenAndShow(new AltScreen(previousScreen));
        }
    }

    private void handleGenerateRandom() {
        CompletableFuture.supplyAsync(UsernameGenerator::generate)
                .thenAccept(name -> {
                    if (name != null) {
                        minecraft.execute(() -> usernameField.setValue(name));
                    }
                });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(font, "Cracked Authentication", width / 2, height / 2 - 60, 0xFFFFFF);
    }
}
