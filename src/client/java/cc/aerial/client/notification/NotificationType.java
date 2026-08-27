package cc.aerial.client.notification;

public enum NotificationType {
    SUCCESS(0xFF87E331, 'H', 1.2f, true),
    ERROR(0xFFFF5050, 'I', 2.2f, false),
    WARNING(0xFFFFD764, 'J', 1.2f, true),
    INFO(0xFFFFFFFF, 'K', 2.2f, true);

    private final int color;
    private final char icon;
    private final float iconOffset;
    private final boolean widensBox;

    NotificationType(int color, char icon, float iconOffset, boolean widensBox) {
        this.color = color;
        this.icon = icon;
        this.iconOffset = iconOffset;
        this.widensBox = widensBox;
    }

    public int getColor() {
        return color;
    }

    public char getIcon() {
        return icon;
    }

    public float getIconOffset() {
        return iconOffset;
    }

    public boolean widensBox() {
        return widensBox;
    }

    public String getLabel() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
