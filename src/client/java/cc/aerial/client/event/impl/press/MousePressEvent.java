package cc.aerial.client.event.impl.press;

public final class MousePressEvent extends LWJGLInteractionEvent {
    public MousePressEvent(int mouseKeyCode) {
        super(mouseKeyCode);
    }

    public MousePressEvent(int mouseKeyCode, boolean pressed) {
        super(mouseKeyCode, pressed);
    }
}
