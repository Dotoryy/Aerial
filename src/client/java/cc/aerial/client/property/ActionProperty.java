package cc.aerial.client.property;

public final class ActionProperty extends Property<Boolean> {
    private final Runnable action;

    public ActionProperty(String name, Runnable action) {
        super(name);
        this.action = action;
    }

    public void run() {
        action.run();
    }
}
