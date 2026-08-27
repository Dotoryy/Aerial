package cc.aerial.client.accountmanager.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

public final class AuthExecutors {
    private AuthExecutors() {
    }

    public static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    public static void shutdown(ExecutorService executor) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
