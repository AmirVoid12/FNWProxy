package iran.flame.network.proxy.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;

public final class SchedulerUtil {

    public interface Cancellable {
        void cancel();
    }

    private SchedulerUtil() {}

    public static void run(ProxyServer proxyServer, Object plugin, Runnable task) {
        proxyServer.getScheduler()
                .buildTask(plugin, task)
                .schedule();
    }

    public static void runAsync(ProxyServer proxyServer, Object plugin, Runnable task) {
        proxyServer.getScheduler()
                .buildTask(plugin, task)
                .schedule();
    }

    public static Cancellable runLater(ProxyServer proxyServer, Object plugin, Runnable task, long delaySeconds) {
        ScheduledTask scheduledTask = proxyServer.getScheduler()
                .buildTask(plugin, task)
                .delay(Duration.ofSeconds(delaySeconds))
                .schedule();
        return scheduledTask::cancel;
    }

    public static Cancellable runTimer(ProxyServer proxyServer, Object plugin, Runnable task, long delaySeconds, long periodSeconds) {
        ScheduledTask scheduledTask = proxyServer.getScheduler()
                .buildTask(plugin, task)
                .delay(Duration.ofSeconds(delaySeconds))
                .repeat(Duration.ofSeconds(periodSeconds))
                .schedule();
        return scheduledTask::cancel;
    }

}