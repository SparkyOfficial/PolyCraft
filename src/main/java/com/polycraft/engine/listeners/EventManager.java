package com.polycraft.engine.listeners;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.event.*;
import org.bukkit.plugin.EventExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager implements Listener, EventExecutor {
    
    private final PolyCraftEngine plugin;
    private final Map<Class<? extends Event>, Set<ScriptHandler>> handlers = new ConcurrentHashMap<>();
    private final Map<ScriptInstance, Set<Class<? extends Event>>> scriptEvents = new ConcurrentHashMap<>();
    
    public EventManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
    }
    
    public void registerEvent(Class<? extends Event> eventClass, ScriptInstance script, String handlerName) {
        // Create handler if it doesn't exist
        handlers.computeIfAbsent(eventClass, k -> ConcurrentHashMap.newKeySet())
                .add(new ScriptHandler(script, handlerName));
        
        // Track events per script for cleanup
        scriptEvents.computeIfAbsent(script, k -> ConcurrentHashMap.newKeySet())
                   .add(eventClass);
        
        // Register with Bukkit if first handler for this event
        if (handlers.get(eventClass).size() == 1) {
            plugin.getServer().getPluginManager().registerEvent(
                eventClass, this, EventPriority.NORMAL, this, plugin, false
            );
        }
    }
    
    public void unregisterEvents(ScriptInstance script) {
        Set<Class<? extends Event>> events = scriptEvents.remove(script);
        if (events != null) {
            for (Class<? extends Event> eventClass : events) {
                Set<ScriptHandler> eventHandlers = handlers.get(eventClass);
                if (eventHandlers != null) {
                    eventHandlers.removeIf(handler -> handler.script == script);
                    
                    // Unregister from Bukkit if no more handlers
                    if (eventHandlers.isEmpty()) {
                        HandlerList.unregisterAll(this);
                        handlers.remove(eventClass);
                    }
                }
            }
        }
    }
    
    @Override
    public void execute(Listener listener, Event event) {
        Set<ScriptHandler> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (ScriptHandler handler : eventHandlers) {
                try {
                    handler.handle(event);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error in event handler " + handler.handlerName + 
                            " for " + event.getEventName() + 
                            " in script " + handler.script.getScriptFile().getName() + 
                            ": " + e.getMessage());
                }
            }
        }
    }
    
    private static class ScriptHandler {
        final ScriptInstance script;
        final String handlerName;
        
        ScriptHandler(ScriptInstance script, String handlerName) {
            this.script = script;
            this.handlerName = handlerName;
        }
        
        void handle(Event event) {
            script.callEvent(handlerName, event);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScriptHandler that = (ScriptHandler) o;
            return script.equals(that.script) && handlerName.equals(that.handlerName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(script, handlerName);
        }
    }
}
