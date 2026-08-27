package cc.aerial.client.event.impl.game.input;

public final class MouseUpdateEvent {
    private double deltaX, deltaY;
    private final double sensitivityMultiplier;
    private final boolean unlockCursorRun;
    private boolean handled;

    public MouseUpdateEvent(double deltaX, double deltaY, double sensitivityMultiplier, boolean unlockCursorRun) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.sensitivityMultiplier = sensitivityMultiplier;
        this.unlockCursorRun = unlockCursorRun;
    }

    public double getDeltaX() {
        return deltaX;
    }

    public void setDeltaX(double deltaX) {
        this.deltaX = deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public void setDeltaY(double deltaY) {
        this.deltaY = deltaY;
    }

    public double getSensitivityMultiplier() {
        return sensitivityMultiplier;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled() {
        this.handled = true;
    }

    public boolean isUnlockCursorRun() {
        return unlockCursorRun;
    }
}
