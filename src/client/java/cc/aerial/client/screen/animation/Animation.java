package cc.aerial.client.screen.animation;

public final class Animation {
    private Easing easing;
    private long duration;
    private long startTime;

    private float startValue;
    private float destinationValue;
    private float value;
    private boolean finished = true;

    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
    }

    private static final float RESET_EPSILON = 0.05f;

    public void run(float destinationValue) {
        long millis = System.currentTimeMillis();
        if (Math.abs(this.destinationValue - destinationValue) > RESET_EPSILON) {
            this.destinationValue = destinationValue;
            reset();
        } else {
            this.finished = millis - this.duration > this.startTime || this.value == destinationValue;
            if (this.finished) {
                this.value = destinationValue;
                return;
            }
        }

        float result = easing.getFunction().apply(getProgress());
        if (duration == 0L) {
            this.value = destinationValue;
        } else if (this.value > destinationValue) {
            this.value = startValue - (startValue - destinationValue) * result;
        } else {
            this.value = startValue + (destinationValue - startValue) * result;
        }

        if (Float.isNaN(value) || !Float.isFinite(value)) {
            this.value = destinationValue;
        }
    }

    public float getProgress() {
        float progress = (float) (System.currentTimeMillis() - startTime) / (float) duration;
        return Math.min(1.0f, Math.max(0.0f, progress));
    }

    public void setEasing(Easing easing) {
        this.easing = easing;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.startValue = value;
        this.finished = false;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
        this.destinationValue = value;
    }

    public boolean isFinished() {
        return finished;
    }
}
