package cc.aerial.client.notification;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.GlyphQuad;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class NotificationManager implements IEventSubscriber {
    public static final NotificationManager INSTANCE = new NotificationManager();

    private static final float HEIGHT = 24.0f;
    private static final float ROW_PITCH = 37.0f;

    private static final float BASE_Y = 50.0f;

    private static final float TEXT_PADDING = 36.0f;

    private static final float TITLE_SIZE = 9.0f;
    private static final float MESSAGE_SIZE = 8.0f;
    private static final float ICON_SIZE = 21.0f;

    private static final float ICON_NUDGE_Y = 1.0f;
    private static final float TITLE_NUDGE_Y = -1.0f;
    private static final float MESSAGE_NUDGE_Y = -0.5f;

    private float iconInkCenter = -1.0f;

    private static final float SLIDE_IN_SPEED = 500.0f;
    private static final float SLIDE_OUT_SPEED = 1000.0f;

    private static final long LINGER_MS = 150L;

    private final List<Notification> notifications = new ArrayList<>();

    private AerialFont font;
    private AerialFont iconFont;
    private long lastFrameNanos;

    private NotificationManager() {
        EventDispatcher.subscribe(this);
    }

    public NotificationBuilder builder(NotificationType type) {
        return new NotificationBuilder(type);
    }

    private Notification publish(Notification notification) {
        for (Notification existing : notifications) {
            if (existing.getMessage().equalsIgnoreCase(notification.getMessage())) {
                existing.resetTimer();
                return existing;
            }
        }
        notification.setExtending(true);
        notification.resetTimer();
        notifications.add(notification);
        return notification;
    }

    private void update(float step) {
        for (int i = notifications.size() - 1; i >= 0; i--) {
            Notification notification = notifications.get(i);
            float targetY = BASE_Y + i * ROW_PITCH;

            if (notification.getY() < targetY) {
                notification.setY(Mth.clamp(notification.getY() + SLIDE_OUT_SPEED * step, 0.0, targetY));
            } else if (notification.getY() > targetY) {
                notification.setY(Mth.clamp(notification.getY() - SLIDE_IN_SPEED * step, targetY, Double.MAX_VALUE));
            }

            double targetWidth = boxWidth(notification);
            if (notification.isExtending() && notification.getX() < targetWidth) {
                notification.setX(Mth.clamp(notification.getX() + SLIDE_IN_SPEED * step, 0.0, targetWidth));

                notification.resetTimer();
            } else {
                notification.setExtending(false);
            }

            if (!notification.isExtending()
                    && notification.elapsed() >= notification.getDelay() + LINGER_MS
                    && notification.getX() > 0.0) {
                notification.setClosing(true);
                notification.setX(notification.getX() - SLIDE_OUT_SPEED * step);
            }

            if (notification.isClosing() && notification.getX() <= 0.0) {
                notifications.remove(i);
            }
        }
    }

    private double boxWidth(Notification notification) {
        float message = font.stringWidth(notification.getMessage() + notification.getWidthSuffix(), MESSAGE_SIZE);
        float title = font.stringWidth(notification.getTitle(), TITLE_SIZE);
        return Math.max(message, title) + TEXT_PADDING;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.NOTIFICATION);
        try {
            renderAll(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void renderAll(Render2DEvent event) {
        if (notifications.isEmpty()) {
            lastFrameNanos = 0L;
            return;
        }
        ensureFontsLoaded();
        update(frameStep());

        GuiGraphicsExtractor extractor = event.extractor();
        float screenWidth = event.width();
        float screenHeight = event.height();

        for (Notification notification : List.copyOf(notifications)) {
            NotificationType type = notification.getType();
            float x = (float) notification.getX();
            float top = screenHeight - (float) notification.getY();
            float slide = screenWidth - x;
            float left = slide - (type.widensBox() ? 2.0f : 0.0f);
            float bottom = top + HEIGHT;

            AerialBlur.drawBlurredRound(extractor, BlurConsumer.NOTIFICATION,
                    left, top, screenWidth - left, HEIGHT, 0.0f);
            RenderUtil.sharpRect(extractor, left, top, screenWidth, bottom, 0x6E000000);

            TextRenderUtil.drawString(extractor, iconFont, String.valueOf(type.getIcon()),
                    slide + type.getIconOffset(), iconTop(top), ICON_SIZE, type.getColor());

            TextRenderUtil.drawString(extractor, font, notification.getTitle(),
                    slide + 24.5f, top + 3.0f + TITLE_NUDGE_Y, TITLE_SIZE, 0xFFFFFFFF);

            String remaining = Notification.formatSeconds(notification.getDelay() - notification.getCount());
            TextRenderUtil.drawString(extractor, font, notification.getMessage() + " (" + remaining + "s)",
                    slide + 25.0f, top + 12.5f + MESSAGE_NUDGE_Y, MESSAGE_SIZE, 0xFFC8C8C8);

            float progress = (float) (notification.getCount() / notification.getDelay());
            RenderUtil.sharpRect(extractor, left, bottom - 1.0f, left + x * progress, bottom,
                    type.getColor());
        }
    }

    private float iconTop(float boxTop) {
        if (iconInkCenter < 0.0f) {
            GlyphQuad[] quad = iconFont.layout("H", 0.0f, 0.0f, ICON_SIZE);
            iconInkCenter = quad.length == 0 ? ICON_SIZE * 0.5f : (quad[0].y0 + quad[0].y1) * 0.5f;
        }
        return boxTop + HEIGHT * 0.5f - iconInkCenter + ICON_NUDGE_Y;
    }

    private float frameStep() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0.0f;
        }
        float step = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        return Math.min(step, 0.1f);
    }

    private void ensureFontsLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
        if (iconFont == null) {
            NotificationType[] types = NotificationType.values();
            char[] glyphs = new char[types.length];
            for (int i = 0; i < types.length; i++) {
                glyphs[i] = types[i].getIcon();
            }
            iconFont = AerialFont.createIconFromResource("stylesicons.ttf", glyphs);
        }
    }

    public static final class NotificationBuilder {
        private final NotificationType type;
        private String title;
        private String message = "";
        private int duration = 2000;

        private NotificationBuilder(NotificationType type) {
            this.type = type;
        }

        public NotificationBuilder title(String title) {
            this.title = title;
            return this;
        }

        public NotificationBuilder description(String description) {
            this.message = description == null ? "" : description;
            return this;
        }

        public NotificationBuilder duration(int duration) {
            this.duration = duration;
            return this;
        }

        public Notification buildAndPublish() {
            String resolved = title == null || title.isEmpty() ? type.getLabel() : title;
            return NotificationManager.INSTANCE.publish(
                    new Notification(type, resolved, message, duration));
        }
    }
}
