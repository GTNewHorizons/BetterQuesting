package betterquesting.handlers;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.apache.commons.lang3.Validate;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ServerTaskScheduler {

    private final ArrayDeque<FutureTask<?>> serverTasks = new ArrayDeque<>();
    private final Thread serverThread;

    public ServerTaskScheduler(Thread thread) {
        this.serverThread = thread;
    }

    @SuppressWarnings("UnstableApiUsage")
    public <T> ListenableFuture<T> scheduleServerTask(Callable<T> task, boolean allowImmediate) {
        Validate.notNull(task);

        if (!allowImmediate || Thread.currentThread() != serverThread) {
            ListenableFutureTask<T> lft = ListenableFutureTask.create(task);

            synchronized (serverTasks) {
                serverTasks.add(lft);
                return lft;
            }
        } else {
            try {
                return Futures.immediateFuture(task.call());
            } catch (Exception exception) {
                return Futures.immediateFailedCheckedFuture(exception);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            synchronized (serverTasks) {
                while (!serverTasks.isEmpty()) {
                    serverTasks.poll()
                        .run();
                }
            }
        }
    }
}
