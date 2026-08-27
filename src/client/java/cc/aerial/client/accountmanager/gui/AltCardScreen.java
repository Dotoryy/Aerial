package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.screen.widget.CardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public abstract class AltCardScreen extends CardScreen {
    protected AltCardScreen(String title, @Nullable Screen previousScreen) {
        super(title, previousScreen);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(new AltScreen(previousScreen));
    }
}
