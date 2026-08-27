package cc.aerial.client.render;

import cc.aerial.client.features.impl.visual.ChatModule;
import cc.aerial.client.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

public final class ChatDecoration {
    private static final float MAX_RADIUS = 6.0f;
    private static final int BACKGROUND = 0x8C0F0F0F;

    private static final float PAD_X = 4.0f;
    private static final float PAD_Y = 3.0f;

    private ChatDecoration() {
    }

    public static void before(GuiGraphicsExtractor extractor) {
        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();

        ChatModule module = ChatModule.INSTANCE;
        if (!module.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        ChatComponent chat = mc.gui.hud.getChat();
        ChatComponentAccessor access = (ChatComponentAccessor) chat;
        float scale = (float) access.aerial$scale();
        if (scale <= 0.0f) {
            return;
        }

        pose.translate(0.0f, module.getSlide() * scale);

        if (!module.isBackground()) {
            return;
        }

        float lineSpan = module.getPanelHeight();
        if (lineSpan <= 0.1f) {
            return;
        }

        float width = Mth.ceil(access.aerial$width() / scale) + PAD_X * 2.0f;
        float bottom = Mth.floor((extractor.guiHeight() - 40) / scale) + PAD_Y;
        float height = lineSpan + PAD_Y * 2.0f;
        float left = -PAD_X;
        float top = bottom - height;

        float radius = Math.min(height / 5.0f, MAX_RADIUS);

        pose.pushMatrix();
        pose.scale(scale, scale);
        AerialBlur.drawGlass(extractor, BlurConsumer.CHAT, left, top, width, height, radius,
                BACKGROUND, 1.0f, null);
        pose.popMatrix();
    }

    public static void after(GuiGraphicsExtractor extractor) {
        extractor.pose().popMatrix();
    }
}
