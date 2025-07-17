package com.polycraft.engine.scheduler;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages scheduled tasks for scripts.
 */
public class ScriptScheduler {
    
    private final PolyCraftEngine plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    
    public ScriptScheduler(PolyCraftEngine plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Schedule a task to run asynchronously.
     * @param script The script that owns this task
     * @param task The task to run
     * @return The task ID
     */
    public UUID runAsync(ScriptInstance script, Runnable task) {
        return runAsync(script, task, 0);
    }
    
    /**
     * Schedule a delayed task to run asynchronously.
     * @param script The script that owns this task
     * @param task The task to run
     * @param delayTicks The delay in ticks (1 second = 20 ticks)
     * @return The task ID
     */
    public UUID runAsync(ScriptInstance script, Runnable task, long delayTicks) {
        return scheduleTask(script, () -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task), delayTicks);
    }
    
    /**
     * Schedule a repeating task to run asynchronously.
     * @param script The script that owns this task
     * @param task The task to run
     * @param delayTicks The delay in ticks before first run
     * @param periodTicks The period between subsequent runs
     * @return The task ID
     */
    public UUID runAsyncTimer(ScriptInstance script, Runnable task, long delayTicks, long periodTicks) {
        return scheduleTask(script, 
            () -> Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks),
            0
        );
    }
    
    /**
     * Schedule a task to run on the main server thread.
     * @param script The script that owns this task
     * @param task The task to run
     * @return The task ID
     */
    public UUID runSync(ScriptInstance script, Runnable task) {
        return runSync(script, task, 0);
    }
    
    /**
     * Schedule a delayed task to run on the main server thread.
     * @param script The script that owns this task
     * @param task The task to run
     * @param delayTicks The delay in ticks (1 second = 20 ticks)
     * @return The task ID
     */
    public UUID runSync(ScriptInstance script, Runnable task, long delayTicks) {
        return scheduleTask(script, () -> Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks), 0);
    }
    
    /**
     * Schedule a repeating task to run on the main server thread.
     * @param script The script that owns this task
     * @param task The task to run
     * @param delayTicks The delay in ticks before first run
     * @param periodTicks The period between subsequent runs
     * @return The task ID
     */
    public UUID runSyncTimer(ScriptInstance script, Runnable task, long delayTicks, long periodTicks) {
        return scheduleTask(script, 
            () -> Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks),
            0
        );
    }
    
    /**
     * Cancel a scheduled task.
     * @param taskId The task ID to cancel
     */
    public void cancelTask(UUID taskId) {
        if (taskId != null) {
            BukkitTask task = tasks.remove(taskId);
            if (task != null) {
                task.cancel();
            }
        }
    }
    
    /**
     * Cancel all tasks for a specific script.
     * @param script The script whose tasks should be cancelled
     */
    public void cancelTasks(ScriptInstance script) {
        if (script != null) {
            // Create a copy to avoid ConcurrentModificationException
            tasks.entrySet().stream()
                .filter(entry -> entry.getValue().getOwner().equals(script))
                .map(Map.Entry::getKey)
                .forEach(this::cancelTask);
        }
    }
    
    /**
     * Cancel all scheduled tasks.
     */
    public void cancelAllTasks() {
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }
    
    private UUID scheduleTask(ScriptInstance script, TaskScheduler scheduler, long delayTicks) {
        UUID taskId = UUID.randomUUID();
        
        Runnable wrappedTask = () -> {
            try {
                BukkitTask task = scheduler.schedule();
                tasks.put(taskId, task);
            } catch (Exception e) {
                plugin.getLogger().severe("Error scheduling task for script: " + script.getScriptFile().getName());
                e.printStackTrace();
            }
        };
        
        if (delayTicks <= 0) {
            wrappedTask.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, wrappedTask, delayTicks);
        }
        
        return taskId;
    }
    
    @FunctionalInterface
    private interface TaskScheduler {
        BukkitTask schedule();
    }
}
