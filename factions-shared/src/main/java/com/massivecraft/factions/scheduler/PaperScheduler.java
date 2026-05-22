package com.massivecraft.factions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class PaperScheduler implements FactionScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String mode() {
        return "paper-bukkit";
    }

    @Override
    public ScheduledTaskHandle runGlobal(Runnable task) {
        return ScheduledTaskHandle.bukkit(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public ScheduledTaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return ScheduledTaskHandle.bukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public ScheduledTaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return ScheduledTaskHandle.bukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTaskHandle runAt(Location location, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public ScheduledTaskHandle runAtLater(Location location, Runnable task, long delayTicks) {
        return runGlobalLater(task, delayTicks);
    }

    @Override
    public ScheduledTaskHandle runForEntity(Entity entity, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public ScheduledTaskHandle runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        return runGlobalLater(task, delayTicks);
    }

    @Override
    public ScheduledTaskHandle runAsync(Runnable task) {
        return ScheduledTaskHandle.bukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public ScheduledTaskHandle runAsyncLater(Runnable task, long delayTicks) {
        return ScheduledTaskHandle.bukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }
}
