package com.polycraft.engine.events;

import com.polycraft.engine.scripting.ScriptEventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Represents a handler registration for an event.
 */
public class HandlerRegistration {
    final Class<? extends Event> eventClass;
    final EventPriority priority;
    final EventExecutor executor;
    final Plugin plugin;
    final boolean ignoreCancelled;
    RegisteredListener registeredListener;

    public HandlerRegistration(Class<? extends Event> eventClass, 
                             EventPriority priority,
                             EventExecutor executor,
                             Plugin plugin,
                             boolean ignoreCancelled) {
        this.eventClass = Objects.requireNonNull(eventClass, "eventClass cannot be null");
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.ignoreCancelled = ignoreCancelled;
        
        // Register the event
        this.registeredListener = new RegisteredListener(
            new Listener() {},
            executor,
            priority,
            plugin,
            ignoreCancelled
        );
        
        try {
            Method method = eventClass.getMethod("getHandlerList");
            HandlerList handlerList = (HandlerList) method.invoke(null);
            handlerList.register(registeredListener);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register event handler for " + eventClass.getName(), e);
        }
    }
    
    /**
     * Unregisters this handler registration.
     */
    public void unregister() {
        try {
            Method method = eventClass.getMethod("getHandlerList");
            HandlerList handlerList = (HandlerList) method.invoke(null);
            handlerList.unregister(registeredListener);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister event handler for " + eventClass.getName());
        }
    }
    
    /**
     * Checks if this registration matches the given event class and handler.
     */
    public boolean matches(Class<? extends Event> eventClass, ScriptEventHandler handler) {
        return this.eventClass.equals(eventClass) && 
               this.executor == handler;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandlerRegistration that = (HandlerRegistration) o;
        return eventClass.equals(that.eventClass) &&
               executor.equals(that.executor);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventClass, executor);
    }
}
