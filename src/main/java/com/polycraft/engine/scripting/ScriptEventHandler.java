package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.graalvm.polyglot.Value;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Represents an event handler for a script.
 */
public class ScriptEventHandler implements EventExecutor {
    private final ScriptInstance script;
    private final Value handler;
    private final Class<? extends Event> eventClass;
    private final String eventName;

    public ScriptEventHandler(ScriptInstance script, Class<? extends Event> eventClass, String eventName, Value handler) {
        this.script = script;
        this.handler = handler;
        this.eventClass = eventClass;
        this.eventName = eventName != null ? eventName : eventClass.getSimpleName();
    }

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        handle(event);
    }
    
    /**
     * Handles the event by executing the associated script function.
     * @param event The event to handle
     */
    public void handle(Event event) {
        if (!eventClass.isInstance(event)) {
            return;
        }

        try {
            // Execute the handler in the script's context
            if (handler != null && handler.canExecute()) {
                script.callScriptFunction(handler, event);
            }
        } catch (Exception e) {
            script.getLogger().log(Level.SEVERE, 
                "Error executing event handler for " + eventName, e);
        }
    }

    public Class<? extends Event> getEventClass() {
        return eventClass;
    }

    public ScriptInstance getScript() {
        return script;
    }

    public Value getHandler() {
        return handler;
    }

    public String getEventName() {
        return eventName;
    }
    
    /**
     * Gets the name of the handler function.
     * @return The name of the handler function, or null if not available
     */
    public String getHandlerName() {
        try {
            if (handler != null && handler.hasMember("name")) {
                Value nameValue = handler.getMember("name");
                if (nameValue != null && nameValue.isString()) {
                    return nameValue.asString();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptEventHandler that = (ScriptEventHandler) o;
        return Objects.equals(script, that.script) &&
               Objects.equals(handler, that.handler) &&
               Objects.equals(eventClass, that.eventClass) &&
               Objects.equals(eventName, that.eventName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(script, handler, eventClass, eventName);
    }
}
