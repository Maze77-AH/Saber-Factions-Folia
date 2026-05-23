package com.massivecraft.factions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FoliaScheduler implements FactionScheduler {

    private final Plugin plugin;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
        Server server = Bukkit.getServer();
        this.globalScheduler = invoke(server, "getGlobalRegionScheduler");
        this.regionScheduler = invoke(server, "getRegionScheduler");
        this.asyncScheduler = invoke(server, "getAsyncScheduler");
    }

    @Override
    public String mode() {
        return "folia";
    }

    @Override
    public ScheduledTaskHandle runGlobal(Runnable task) {
        return reflectedTask(globalScheduler, "run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, asConsumer(task));
    }

    @Override
    public ScheduledTaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return reflectedTask(globalScheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, asConsumer(task), sanitizeTicks(delayTicks));
    }

    @Override
    public ScheduledTaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return reflectedTask(globalScheduler, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class}, plugin, asConsumer(task), sanitizeTicks(delayTicks), sanitizeTicks(periodTicks));
    }

    @Override
    public ScheduledTaskHandle runAt(Location location, Runnable task) {
        return reflectedTask(regionScheduler, "run", new Class<?>[]{Plugin.class, Location.class, Consumer.class}, plugin, location, asConsumer(task));
    }

    @Override
    public ScheduledTaskHandle runAtLater(Location location, Runnable task, long delayTicks) {
        return reflectedTask(regionScheduler, "runDelayed", new Class<?>[]{Plugin.class, Location.class, Consumer.class, long.class}, plugin, location, asConsumer(task), sanitizeTicks(delayTicks));
    }

    @Override
    public ScheduledTaskHandle runForEntity(Entity entity, Runnable task) {
        Object scheduler = invoke(entity, "getScheduler");
        return reflectedTask(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class}, plugin, asConsumer(task), noop());
    }

    @Override
    public ScheduledTaskHandle runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        Object scheduler = invoke(entity, "getScheduler");
        return reflectedTask(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class}, plugin, asConsumer(task), noop(), sanitizeTicks(delayTicks));
    }

    @Override
    public ScheduledTaskHandle runForEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        Object scheduler = invoke(entity, "getScheduler");
        return reflectedTask(scheduler, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class, long.class}, plugin, asConsumer(task), noop(), sanitizeTicks(delayTicks), sanitizeTicks(periodTicks));
    }

    @Override
    public ScheduledTaskHandle runAsync(Runnable task) {
        return reflectedTask(asyncScheduler, "runNow", new Class<?>[]{Plugin.class, Consumer.class}, plugin, asConsumer(task));
    }

    @Override
    public ScheduledTaskHandle runAsyncLater(Runnable task, long delayTicks) {
        long delayMillis = sanitizeTicks(delayTicks) * 50L;
        return reflectedTask(asyncScheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class}, plugin, asConsumer(task), delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Folia's global, region, entity, and async schedulers all reject initial delays
     * and periods that are {@code <= 0} with {@code IllegalArgumentException}. The
     * Bukkit/Paper scheduler tolerates {@code 0} ("run on next tick"), so call sites
     * across the codebase pass {@code 0L} for "as soon as possible". Normalize here
     * to a single tick so the abstraction behaves the same on Paper and Folia.
     *
     * <p>Visible for tests.</p>
     */
    static long sanitizeTicks(long ticks) {
        return ticks > 0L ? ticks : 1L;
    }

    private static Consumer<Object> asConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Runnable noop() {
        return () -> {
        };
    }

    private static ScheduledTaskHandle reflectedTask(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        return ScheduledTaskHandle.reflected(invoke(target, methodName, parameterTypes, args));
    }

    private static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            throw new IllegalStateException("Cannot call " + methodName + " on a null scheduler target.");
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to call Folia scheduler method " + methodName + ".", exception);
        }
    }
}
