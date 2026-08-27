package cc.aerial.client.screen.animation;

public final class Scroller {
    private final Animation animation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private float value;

    public Animation getAnimation() {
        return animation;
    }

    public void onScroll(float maxOffset) {
        value = Math.min(0, Math.max(-maxOffset, value));
        animation.run(value);
    }

    public void addScroll(double verticalScroll, float maxOffset) {
        value += (float) (verticalScroll * 50);
        value = Math.max(-maxOffset, value);
        value = Math.min(0, value);
        animation.run(value);
    }
}
