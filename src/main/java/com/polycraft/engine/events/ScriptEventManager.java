package com.polycraft.engine.events;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptEventHandler;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.event.*;
import org.bukkit.plugin.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptEventManager {
    private final PolyCraftEngine plugin;
    private final Map<ScriptInstance, Set<HandlerRegistration>> registeredHandlers;

    public ScriptEventManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.registeredHandlers = new ConcurrentHashMap<>();
    }

    public <T extends Event> void registerEvent(Class<T> eventClass, ScriptEventHandler handler, ScriptInstance script) {
        if (eventClass == null || handler == null || script == null) {
            throw new IllegalArgumentException("Event class, handler, and script must not be null");
        }
        
        EventPriority priority = EventPriority.NORMAL;
        boolean ignoreCancelled = false;
        
        // Create the event executor
        EventExecutor executor = (listener, event) -> {
            if (eventClass.isInstance(event)) {
                handler.handle(event);
            }
        };
        
        try {
            // Register the event
            HandlerRegistration registration = new HandlerRegistration(
                eventClass, 
                priority, 
                executor, 
                plugin, 
                ignoreCancelled
            );
            
            // Store the registration
            registeredHandlers.computeIfAbsent(script, k -> ConcurrentHashMap.newKeySet()).add(registration);
            
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, 
                "Failed to register event handler for " + eventClass.getSimpleName(), e);
            throw e;
        }
    }

    public void unregisterEvents(ScriptInstance script) {
        Set<HandlerRegistration> handlers = registeredHandlers.remove(script);
        if (handlers != null) {
            handlers.forEach(HandlerRegistration::unregister);
        }
    }

    public void unregisterEvent(Class<? extends Event> eventClass, ScriptEventHandler handler) {
        if (eventClass == null || handler == null) {
            return;
        }
        
        ScriptInstance script = handler.getScript();
        Set<HandlerRegistration> handlers = registeredHandlers.get(script);
        if (handlers != null) {
            handlers.removeIf(registration -> {
                if (registration.matches(eventClass, handler)) {
                    registration.unregister();
                    return true;
                }
                return false;
            });
            
            // Remove the script if no more handlers
            if (handlers.isEmpty()) {
                registeredHandlers.remove(script);
            }
        }
    }
    
    /**
     * Unregister all event handlers.
     */
    public void unregisterAll() {
        // Make a copy to avoid concurrent modification
        for (ScriptInstance script : new HashSet<>(registeredHandlers.keySet())) {
            unregisterEvents(script);
        }
        registeredHandlers.clear();
    }
    
    /**
     * Get all registered event classes.
     */
    public Set<Class<? extends Event>> getRegisteredEvents() {
        Set<Class<? extends Event>> registeredEvents = new HashSet<>();
        for (Set<HandlerRegistration> handlers : registeredHandlers.values()) {
            for (HandlerRegistration registration : handlers) {
                registeredEvents.add(registration.eventClass);
            }
        }
        return registeredEvents;
    }
    
    /**
     * Get all registered listeners for a script.
     */
    public Set<RegisteredListener> getListeners(ScriptInstance script) {
        Set<HandlerRegistration> handlers = registeredHandlers.get(script);
        if (handlers != null) {
            Set<RegisteredListener> listeners = new HashSet<>();
            for (HandlerRegistration registration : handlers) {
                listeners.add(registration.registeredListener);
            }
            return listeners;
        }
        return Collections.emptySet();
    }
    
    /**
     * Clean up resources.
     */
    public void shutdown() {
        unregisterAll();
        HandlerList.unregisterAll(plugin);
    }
}
