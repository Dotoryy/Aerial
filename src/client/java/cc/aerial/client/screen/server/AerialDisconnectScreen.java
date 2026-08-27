package cc.aerial.client.screen.server;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class AerialDisconnectScreen extends Screen {
    private final @Nullable Screen parent;
    private final String reason;
    private final @Nullable ServerData server;
    private final String[] actions;

    public AerialDisconnectScreen(@Nullable Screen parent, Component title, String reason) {
        super(title);
        this.parent = parent;
        this.reason = reason;

        this.server = Minecraft.getInstance().getCurrentServer();
        this.actions = server != null
                ? new String[]{"Reconnect", "Back", "Main menu"}
                : new String[]{"Back", "Main menu"};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        DisconnectCard.draw(extractor, width, height, title.getString(), reason, actions,
                mouseX, mouseY);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int index = DisconnectCard.actionAt(width, height, event.x(), event.y(), reason, actions);
        if (index < 0) {
            return super.mouseClicked(event, doubled);
        }
        Minecraft mc = Minecraft.getInstance();
        switch (actions[index]) {
            case "Reconnect" -> {
                if (server != null) {
                    ConnectScreen.startConnecting(parent, mc,
                            ServerAddress.parseString(server.ip), server, false, null);
                }
            }

            case "Back" -> mc.setScreenAndShow(parent);
            default -> mc.setScreenAndShow(new TitleScreen());
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
