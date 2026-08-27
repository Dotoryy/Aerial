package cc.aerial.client.event.impl.press;

class LWJGLInteractionEvent extends cc.aerial.client.event.EventCancellable {
    private final int interactionCode;
    private final boolean pressed;

    protected LWJGLInteractionEvent(int interactionCode) {
        this(interactionCode, true);
    }

    protected LWJGLInteractionEvent(int interactionCode, boolean pressed) {
        this.interactionCode = interactionCode;
        this.pressed = pressed;
    }

    public int getInteractionCode() {
        return interactionCode;
    }

    public boolean isPressed() {
        return pressed;
    }
}
