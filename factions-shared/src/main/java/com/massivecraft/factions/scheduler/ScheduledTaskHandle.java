package com.massivecraft.factions.scheduler;

import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;

public interface ScheduledTaskHandle {

    ScheduledTaskHandle NOOP = new ScheduledTaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void cancel();

    boolean isCancelled();

    static ScheduledTaskHandle bukkit(BukkitTask task) {
        return new ScheduledTaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    static ScheduledTaskHandle reflected(Object task) {
        if (task == null) {
            return NOOP;
        }

        return new ScheduledTaskHandle() {
            @Override
            public void cancel() {
                invokeBooleanMethod(task, "cancel");
            }

            @Override
            public boolean isCancelled() {
                return invokeBooleanMethod(task, "isCancelled");
            }
        };
    }

    static boolean invokeBooleanMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
