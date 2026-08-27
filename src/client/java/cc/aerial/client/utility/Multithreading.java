package cc.aerial.client.utility;

import net.minecraft.client.Minecraft;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Multithreading {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Aerial Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private Multithreading() {
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        SCHEDULER.schedule(() -> Minecraft.getInstance().execute(task), delay, unit);
    }
}
