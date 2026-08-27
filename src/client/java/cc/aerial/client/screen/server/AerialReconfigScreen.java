package cc.aerial.client.screen.server;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

public final class AerialReconfigScreen extends Screen {
    private static final String DISCONNECT = "Disconnect";

    private final Connection connection;

    public AerialReconfigScreen(Component title, Connection connection) {
        super(title);
        this.connection = connection;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        LoadingCard.draw(extractor, width, height, "reconfiguring",
                title == null ? null : title.getString(), -1.0f, DISCONNECT, mouseX, mouseY);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (LoadingCard.isActionHovered(width, height, event.x(), event.y(), DISCONNECT, true)) {
            connection.disconnect(Component.translatable("menu.disconnect"));
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
