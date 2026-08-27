package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.mixin.ChatComponentAccessor;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;

import java.util.List;

public final class ChatModule extends Module {
    public static final ChatModule INSTANCE = new ChatModule();

    private static final int DEFAULT_WIDTH = 320;

    private final NumberProperty width = new NumberProperty("Width", 320.0, 40.0, 320.0, 1.0);
    private final NumberProperty openHeight = new NumberProperty("Open Height", 180.0, 20.0, 300.0, 1.0);
    private final NumberProperty closedHeight = new NumberProperty("Max Closed Height", 90.0, 20.0, 300.0, 1.0);

    private final NumberProperty disappear = new NumberProperty("Disappear After", 10.0, 1.0, 30.0, 0.5);
    private final BooleanProperty background = new BooleanProperty("Background", true);
    private final BooleanProperty smooth = new BooleanProperty("Smooth", true);
    private final BooleanProperty hidePlayers = new BooleanProperty("Hide Player Messages", false);

    private final Animation slide = new Animation(Easing.EASE_OUT_EXPO, 500);

    private final Animation panelHeight = new Animation(Easing.EASE_OUT_EXPO, 500);

    private ChatModule() {
        super("Chat", "Restyles and resizes the chat", ModuleCategory.VISUAL);
        addProperties(width, openHeight, closedHeight, disappear, background, smooth, hidePlayers);
    }

    public int getChatWidth() {
        return isEnabled() ? width.getValue().intValue() : DEFAULT_WIDTH;
    }

    public int getChatHeight(boolean focused) {
        if (!isEnabled()) {
            return focused ? 180 : 90;
        }
        return (focused ? openHeight : closedHeight).getValue().intValue();
    }

    public double getDisappearTicks() {
        return isEnabled() ? disappear.getValue() * 20.0 : 200.0;
    }

    public boolean isBackground() {
        return isEnabled() && background.getValue();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        ChatComponent chat = mc.gui.hud.getChat();
        int visible = visibleLines(chat, mc.gui.hud.getGuiTicks());

        if (!smooth.getValue()) {
            slide.setValue(0.0f);
            panelHeight.setValue(visible * LINE_HEIGHT);
            return;
        }
        slide.run(0.0f);
        panelHeight.run(visible * LINE_HEIGHT);
    }

    public void onLinesAdded(int lines, boolean focused) {
        if (!isEnabled() || !smooth.getValue()) {
            return;
        }

        float limit = getChatHeight(focused);
        slide.setValue(Math.min(slide.getValue() + lines * LINE_HEIGHT, limit));
    }

    public float getSlide() {
        return isEnabled() && smooth.getValue() ? slide.getValue() : 0.0f;
    }

    public float getPanelHeight() {
        return panelHeight.getValue();
    }

    private int visibleLines(ChatComponent chat, int tickCount) {
        ChatComponentAccessor access = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> trimmed = access.aerial$trimmedMessages();
        int scroll = access.aerial$scrollPos();
        int perPage = chat.getLinesPerPage();
        boolean focused = chat.isChatFocused();
        double window = getDisappearTicks();

        int visible = 0;
        for (int i = 0; i < perPage && i + scroll < trimmed.size(); i++) {
            if (!focused) {
                double age = tickCount - trimmed.get(i + scroll).addedTime();
                double alpha = Mth.clamp((1.0 - age / window) * 10.0, 0.0, 1.0);
                if (alpha * alpha <= 1.0E-5) {
                    break;
                }
            }
            visible++;
        }
        return visible;
    }

    public static final float LINE_HEIGHT = 9.0f;

    @Override
    protected void onEnable() {
        applyFilter();
    }

    @Override
    protected void onDisable() {
        applyFilter();
    }

    private void applyFilter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        boolean hide = isEnabled() && hidePlayers.getValue();
        mc.gui.hud.getChat().setVisibleMessageFilter(hide ? ChatModule::isNotPlayerMessage : message -> true);
    }

    private static boolean isNotPlayerMessage(GuiMessage message) {
        return !message.content().getString().contains(":");
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        boolean hide = hidePlayers.getValue();
        if (hide != filterApplied) {
            filterApplied = hide;
            applyFilter();
        }
    }

    private boolean filterApplied;
}
