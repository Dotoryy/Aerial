package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.ChatUtility;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.Multithreading;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;

public final class AutoHypixelModule extends Module {
    public static final AutoHypixelModule INSTANCE = new AutoHypixelModule();

    private static final String AUTO_GG_MESSAGE = "gg";

    private final BooleanProperty autoGGEnabled = new BooleanProperty("Enabled", true);
    private final BooleanProperty autoPlayEnabled = new BooleanProperty("Enabled", true);
    private final NumberProperty autoPlayDelay = new NumberProperty("Delay", 2.5, 0, 8, 0.5).hideIf(() -> !autoPlayEnabled.getValue());
    private final BooleanProperty autoLeaveOnPlayerBan = new BooleanProperty("Auto Leave On Ban", false);

    private long lastAutoGGMessage;

    private AutoHypixelModule() {
        super("Auto Hypixel", "Useful features for Hypixel", ModuleCategory.UTILITY);
        addProperties(
                new GroupProperty("Auto GG", autoGGEnabled),
                new GroupProperty("Auto Play", autoPlayEnabled, autoPlayDelay),
                autoLeaveOnPlayerBan
        );
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        if (!HypixelServer.isCurrent()) {
            return;
        }

        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (location == null) {
            return;
        }

        String message = event.getText().getString();

        if (autoLeaveOnPlayerBan.getValue() && message.equals("A player has been removed from your game.")) {
            ChatUtility.sendCommand("l");
            NotificationManager.INSTANCE.builder(NotificationType.INFO)
                    .title(getName())
                    .description("A player in your game got banned.")
                    .duration(2000)
                    .buildAndPublish();
            return;
        }

        if (autoGGEnabled.getValue() && System.currentTimeMillis() - lastAutoGGMessage > 5000
                && HypixelServer.KARMA_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(message).matches())) {
            ChatUtility.sendCommand("ac " + AUTO_GG_MESSAGE);
            lastAutoGGMessage = System.currentTimeMillis();
        }

        if (autoPlayEnabled.getValue()) {
            if (message.equals("Queued! Use the bed to cancel!")) {
                scheduleAutoPlay();
            } else if (!location.isLobby()) {
                for (Component sibling : event.getText().getSiblings()) {
                    ClickEvent clickEvent = sibling.getStyle().getClickEvent();
                    if (!(clickEvent instanceof ClickEvent.RunCommand runCommand)) {
                        continue;
                    }
                    if (!runCommand.command().startsWith("/play ")) {
                        continue;
                    }
                    scheduleAutoPlay();
                    break;
                }
            }
        }
    }

    private void scheduleAutoPlay() {
        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (location == null) {
            return;
        }

        double delay = autoPlayDelay.getValue();
        int delayMs = (int) (delay * 1000);

        Multithreading.schedule(() -> ChatUtility.sendCommand("play " + location.mode()), delayMs, TimeUnit.MILLISECONDS);

        NotificationManager.INSTANCE.builder(NotificationType.SUCCESS)
                .title(getName())
                .description("Auto Play" + (delay > 0 ? " in " + delay + "s" : "") + "!")
                .duration(delayMs + 200)
                .buildAndPublish();
    }
}
