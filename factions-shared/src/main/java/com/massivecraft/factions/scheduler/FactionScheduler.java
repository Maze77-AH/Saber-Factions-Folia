package com.massivecraft.factions.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface FactionScheduler {

    String mode();

    ScheduledTaskHandle runGlobal(Runnable task);

    ScheduledTaskHandle runGlobalLater(Runnable task, long delayTicks);

    ScheduledTaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    ScheduledTaskHandle runAt(Location location, Runnable task);

    ScheduledTaskHandle runAtLater(Location location, Runnable task, long delayTicks);

    ScheduledTaskHandle runForEntity(Entity entity, Runnable task);

    ScheduledTaskHandle runForEntityLater(Entity entity, Runnable task, long delayTicks);

    ScheduledTaskHandle runForEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks);

    ScheduledTaskHandle runAsync(Runnable task);

    ScheduledTaskHandle runAsyncLater(Runnable task, long delayTicks);
}
