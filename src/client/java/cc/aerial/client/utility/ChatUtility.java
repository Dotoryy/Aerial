package cc.aerial.client.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

public final class ChatUtility {
    private ChatUtility() {
    }

    public static void print(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return;
        }
        minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
    }

    public static void sendCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
    }
}
