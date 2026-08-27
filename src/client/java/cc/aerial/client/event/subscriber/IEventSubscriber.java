package cc.aerial.client.event.subscriber;

public interface IEventSubscriber {
    default boolean isHandlingEvents() {
        return true;
    }
}
