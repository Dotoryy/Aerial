package cc.aerial.client.render;

public enum BlurConsumer {
    ARRAYLIST("Arraylist"),
    TARGET_HUD("TargetHUD"),
    RISE_CAPSULE("Rise Capsule"),
    SPOTIFY("Spotify"),
    SCOREBOARD("Scoreboard"),
    CHAT("Chat"),
    POTION_EFFECTS("Potion Effects"),
    DYNAMIC_ISLAND("Dynamic Island"),
    SCAFFOLD_COUNTER("Scaffold Counter"),
    CLICK_GUI("ClickGUI"),
    NOTIFICATION("Notifications"),
    OVERLAY("Overlay");

    public static final BlurConsumer[] VALUES = values();

    private final String label;

    BlurConsumer(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
