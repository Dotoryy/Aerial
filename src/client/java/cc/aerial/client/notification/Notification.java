package cc.aerial.client.notification;

import net.minecraft.util.Mth;

public final class Notification {
    private final NotificationType type;
    private final String title;
    private final String message;
    private final int delay;
    private final String widthSuffix;

    private double x;
    private double y = 50.0;
    private boolean extending;

    private boolean closing;
    private long lastReset = System.currentTimeMillis();

    Notification(NotificationType type, String title, String message, int delay) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.delay = delay;
        this.widthSuffix = buildWidthSuffix(delay);
    }

    private static String buildWidthSuffix(int delayMs) {
        return " (" + formatSeconds(delayMs) + "s) ";
    }

    static String formatSeconds(double milliseconds) {
        return String.format("%.1f", milliseconds / 1000.0);
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public int getDelay() {
        return delay;
    }

    public String getWidthSuffix() {
        return widthSuffix;
    }

    public double getX() {
        return x;
    }

    void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    void setY(double y) {
        this.y = y;
    }

    public boolean isExtending() {
        return extending;
    }

    void setExtending(boolean extending) {
        this.extending = extending;
    }

    boolean isClosing() {
        return closing;
    }

    void setClosing(boolean closing) {
        this.closing = closing;
    }

    void resetTimer() {
        this.lastReset = System.currentTimeMillis();
    }

    long elapsed() {
        return System.currentTimeMillis() - lastReset;
    }

    public double getCount() {
        return Mth.clamp((double) elapsed(), 0.0, delay);
    }
}
