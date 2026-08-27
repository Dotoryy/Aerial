package cc.aerial.client.event;

import cc.aerial.client.event.registry.EventRegistry;

public final class EventDispatcher {
    private static final EventRegistry EVENT_REGISTRY = new EventRegistry();

    private EventDispatcher() {
    }

    public static void subscribe(final Object subscriber) {
        EVENT_REGISTRY.subscribe(subscriber);
    }

    public static void dispatch(final Object event) {
        EVENT_REGISTRY.dispatch(event);
    }
}
