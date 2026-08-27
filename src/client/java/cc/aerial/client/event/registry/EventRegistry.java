package cc.aerial.client.event.registry;

import cc.aerial.client.event.listener.ListenerMethod;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EventRegistry {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Map<Class<?>, List<ListenerMethod>> subscriberMap = new ConcurrentHashMap<>();

    public void subscribe(final Object subscriber) {
        final Map<Class<?>, List<ListenerMethod>> collected = new HashMap<>();
        this.collect(subscriber, subscriber.getClass(), collected);

        Class<?> parent = subscriber.getClass().getSuperclass();
        while (parent != null && parent != Object.class) {
            this.collect(subscriber, parent, collected);
            parent = parent.getSuperclass();
        }

        for (final Map.Entry<Class<?>, List<ListenerMethod>> entry : collected.entrySet()) {
            subscriberMap.merge(entry.getKey(), sortedUnion(List.of(), entry.getValue()),
                    EventRegistry::sortedUnion);
        }
    }

    private static List<ListenerMethod> sortedUnion(final List<ListenerMethod> existing,
                                                    final List<ListenerMethod> added) {
        final List<ListenerMethod> merged = new ArrayList<>(existing.size() + added.size());
        merged.addAll(existing);
        merged.addAll(added);
        merged.sort(Comparator.comparingInt(ListenerMethod::getPriority));
        return List.copyOf(merged);
    }

    private void collect(final Object instance, final Class<?> owner,
                         final Map<Class<?>, List<ListenerMethod>> into) {
        final IEventSubscriber listener = (IEventSubscriber) instance;

        for (final Method method : owner.getDeclaredMethods()) {
            final Subscribe subscribe = method.getDeclaredAnnotation(Subscribe.class);
            if (subscribe == null || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalStateException(
                        "@Subscribe method must take exactly one event argument: " + owner.getName() + "#" + method.getName());
            }

            final Class<?> eventType = method.getParameterTypes()[0];
            final MethodType methodType = MethodType.methodType(void.class, eventType);

            try {
                final MethodHandles.Lookup lookup = Modifier.isPrivate(method.getModifiers())
                        ? MethodHandles.privateLookupIn(owner, LOOKUP)
                        : LOOKUP;

                final MethodHandle handle = lookup.findVirtual(owner, method.getName(), methodType);
                into.computeIfAbsent(eventType, key -> new ArrayList<>())
                        .add(new ListenerMethod(subscribe.priority(), new ConstantCallSite(handle), listener));
            } catch (IllegalAccessException | NoSuchMethodException e) {
                throw new RuntimeException("Error subscribing event listener: " + method.getName(), e);
            }
        }
    }

    public void dispatch(final Object event) {
        final List<ListenerMethod> listeners = subscriberMap.get(event.getClass());
        if (listeners == null) {
            return;
        }

        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i).invoke(event)) {
                break;
            }
        }
    }
}
