package cc.aerial.client.accountmanager.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AddAccountScreen extends Screen {
    private final Screen previousScreen;

    public AddAccountScreen(Screen previousScreen) {
        super(Component.literal("Add Account"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 45;
        addRenderableWidget(Button.builder(Component.literal("Microsoft"), b -> minecraft.setScreenAndShow(new MicrosoftAuthScreen(previousScreen)))
                .pos(x, y).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cookie"), b -> minecraft.setScreenAndShow(new CookieAuthScreen(previousScreen)))
                .pos(x, y + 25).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cracked"), b -> minecraft.setScreenAndShow(new CrackedAuthScreen(previousScreen)))
                .pos(x, y + 50).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Access Token"), b -> minecraft.setScreenAndShow(new TokenLoginScreen(previousScreen)))
                .pos(x, y + 75).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh Token"), b -> minecraft.setScreenAndShow(new RefreshTokenLoginScreen(previousScreen)))
                .pos(x, y + 100).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreenAndShow(new AccountManagerScreen(previousScreen)))
                .pos(x, y + 125).size(200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(font, "Choose Account Type to Add", width / 2, height / 2 - 70, 0xFFFFFF);
    }
}
