package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.utility.HypixelServer;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

public final class StreamerModule extends Module {
    public static final StreamerModule INSTANCE = new StreamerModule();

    private final BooleanProperty hideServerId = new BooleanProperty("Hide server ID", true);
    private final BooleanProperty hideUsername = new BooleanProperty("Hide username", true);
    private final StringProperty customUsername = new StringProperty("Custom username", "You");

    private StreamerModule() {
        super("Streamer", "Features for content creators", ModuleCategory.VISUAL);
        addProperties(hideServerId, hideUsername, customUsername);
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        if (!hideServerId.getValue() || !HypixelServer.isCurrent()) {
            return;
        }
        String message = event.getText().getString();
        if (message.startsWith("Sending you to ")) {
            event.setCancelled();
            String serverId = message.replace("Sending you to ", "").replace("!", "");

            NotificationManager.INSTANCE.builder(NotificationType.INFO)
                    .title("Sending you to")
                    .description("*".repeat(serverId.length()))
                    .buildAndPublish();
        }
    }

    public String filter(String text) {
        if (!isEnabled() || !hideUsername.getValue()) {
            return text;
        }
        String realUsername = Minecraft.getInstance().getUser().getName();
        return StringUtils.replaceIgnoreCase(text, realUsername, customUsername.getValue());
    }

    public boolean isHidingServerId() {
        return isEnabled() && hideServerId.getValue();
    }
}
