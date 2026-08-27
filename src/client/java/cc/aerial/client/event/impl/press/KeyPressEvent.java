package cc.aerial.client.event.impl.press;

public class KeyPressEvent extends LWJGLInteractionEvent {
    public KeyPressEvent(int keyCode) {
        super(keyCode);
    }

    public KeyPressEvent(int keyCode, boolean pressed) {
        super(keyCode, pressed);
    }
}
