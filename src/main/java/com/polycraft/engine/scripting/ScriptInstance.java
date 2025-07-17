package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.api.PolyAPI;
import com.polycraft.engine.config.ScriptConfig;
import com.polycraft.engine.data.ScriptDataManager;
import com.polycraft.engine.listeners.EventManager;
import com.polycraft.engine.scheduler.ScriptScheduler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import java.nio.file.Paths;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import com.polycraft.engine.config.ScriptConfig;
import com.polycraft.engine.data.ScriptDataManager;
import com.polycraft.engine.listeners.EventManager;
import com.polycraft.engine.scheduler.ScriptScheduler;
import com.polycraft.engine.security.ScriptSecurityManager;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.Event;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents a single script instance with its own execution context and state.
 * Handles script lifecycle, event registration, and provides API bindings.
 */
public class ScriptInstance implements AutoCloseable {
    
    // Core components
    private final PolyCraftEngine plugin;
    private final File scriptFile;
    private final ScriptScheduler scheduler;
    private final Map<String, Object> scriptData = new ConcurrentHashMap<>();
    private final Map<String, List<ScriptEventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final Map<String, Object> registeredCommands = new ConcurrentHashMap<>();
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean isDisabling = new AtomicBoolean(false);
    private final Map<Class<? extends Event>, Set<String>> registeredEvents = new ConcurrentHashMap<>();
    private final Set<AutoCloseable> resources = ConcurrentHashMap.newKeySet();
    private final AtomicReference<ScriptState> state = new AtomicReference<>(ScriptState.CREATED);
    private volatile Context context;
    private volatile Value bindings;
    private volatile Value polyObject;
    private ScriptLanguage language;
    private volatile boolean enabled = false;
    private volatile long lastModified;
    private volatile Instant lastEnabledTime;
    private volatile Instant lastDisabledTime;
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final EventManager eventManager;
    private final ScriptConfig config;
    private final ScriptDataManager dataManager;
    private final PolyAPI api;
    private final ScriptSecurityManager securityManager;
    private final ThreadLocal<Boolean> isExecuting = ThreadLocal.withInitial(() -> false);
    private final ExecutorService asyncExecutor;
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final boolean isThreadCpuTimeEnabled = threadMXBean.isThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled();
    private final Map<Long, Long> threadCpuStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    
    /**
     * Gets the language of this script.
     * @return The script language
     */
    public ScriptLanguage getLanguage() {
        return language;
    }

    /**
     * Creates a new script instance.
     * 
     * @param plugin The PolyCraftEngine instance
     * @param scriptFile The script file
     * @param scheduler The script scheduler
     * @param eventManager The event manager for handling script events
     * @param config The script configuration
     * @param dataManager The script data manager
     * @throws IllegalArgumentException if any required parameter is null
     * @throws FileNotFoundException if scriptFile doesn't exist
     */
    public ScriptInstance(PolyCraftEngine plugin, File scriptFile, ScriptScheduler scheduler,
                         EventManager eventManager, ScriptConfig config, ScriptDataManager dataManager) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (scriptFile == null) {
            throw new IllegalArgumentException("Script file cannot be null");
        }
        if (scheduler == null) {
            throw new IllegalArgumentException("Scheduler cannot be null");
        }
        if (eventManager == null) {
            throw new IllegalArgumentException("Event manager cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (dataManager == null) {
            throw new IllegalArgumentException("Data manager cannot be null");
        }
        if (!scriptFile.exists()) {
            throw new RuntimeException(new FileNotFoundException("Script file not found: " + scriptFile.getAbsolutePath()));
        }
        
        this.plugin = plugin;
        this.scriptFile = scriptFile;
        this.scheduler = scheduler;
        this.eventManager = eventManager;
        this.config = config;
        this.dataManager = dataManager;
        
        // Initialize the async executor with the script file name
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Script-Async-" + scriptFile.getName());
            t.setDaemon(true);
            return t;
        });
        
        // Initialize language
        ScriptLanguage detectedLanguage = ScriptLanguage.fromFileName(scriptFile.getName());
        this.language = detectedLanguage != null ? detectedLanguage : ScriptLanguage.JAVASCRIPT;
        
        try {
            // Initialize dependencies
            this.securityManager = new ScriptSecurityManager(plugin);
            this.api = new PolyAPI(plugin);
            
            // Initialize metrics
            this.metrics.put("startTime", System.currentTimeMillis());
            this.metrics.put("executionCount", 0L);
            this.lastModified = scriptFile.lastModified();
            
            // Initialize context and bindings
            this.context = createContext();
            this.bindings = context.getBindings(language.getGraalId());
            
            // Register shutdown hook for resource cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(this::safeClose, "Script-Shutdown-" + scriptFile.getName()));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize script: " + scriptFile.getName(), e);
            throw new RuntimeException("Failed to initialize script", e);
        }
    }
    
    /**
     * Unregisters a command that was previously registered by this script.
     * @param command The command name to unregister
     */
    public void unregisterCommand(String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        try {
            // Get the command map from the server
            Server server = Bukkit.getServer();
            Method commandMapMethod = server.getClass().getMethod("getCommandMap");
            CommandMap commandMap = (CommandMap) commandMapMethod.invoke(server);
            
            // Remove the command from the known commands map
            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands != null) {
                knownCommands.remove(command.toLowerCase());
                
                // Also remove any aliases
                for (Iterator<Map.Entry<String, Command>> it = knownCommands.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Command> entry = it.next();
                    if (entry.getKey().startsWith(command.toLowerCase() + ":")) {
                        it.remove();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to unregister command: " + command, e);
        }
    }
    
    /**
     * Helper method to get the known commands map using reflection.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            return (Map<String, Command>) knownCommandsField.get(commandMap);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get known commands map", e);
            return null;
        }
    }
    
    /**
     * Represents the possible states of a script instance.
     */
    public enum ScriptState {
        /** Script has been created but not initialized. */
        CREATED,
        
        /** Script is currently initializing. */
        INITIALIZING,
        
        /** Script is enabled and running. */
        ENABLED,
        
        /** Script is being disabled. */
        DISABLING,
        
        /** Script has been disabled. */
        DISABLED,
        
        /** Script encountered an error. */
        ERROR
    }
    
    /**
     * Initializes and enables the script.
     * 
     * @return true if initialization was successful, false otherwise
     * @throws IllegalStateException if the script is already initialized or failed to initialize
     */
    public boolean initialize() {
        if (!isInitializing.compareAndSet(false, true)) {
            plugin.getLogger().warning("Script initialization already in progress: " + scriptFile.getName());
            return enabled;
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            // Set initial state
            if (state.get() == ScriptState.ERROR) {
                plugin.getLogger().warning("Cannot initialize script in ERROR state: " + scriptFile.getName());
                return false;
            }
            
            state.set(ScriptState.INITIALIZING);
            
            // Create context and bindings
            this.context = createContext();
            this.bindings = context.getBindings(language.getGraalId());
            
            // Set up the API
            setupPolyAPI();
            
            // Load and execute the script
            String scriptContent = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder(language.getGraalId(), scriptContent, scriptFile.getName()).build());
            
            // Call onEnable if it exists
            if (polyObject != null && polyObject.hasMember("onEnable")) {
                polyObject.invokeMember("onEnable");
            }
            
            state.set(ScriptState.ENABLED);
            enabled = true;
            lastEnabledTime = Instant.now();
            
            // Log success
            long duration = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            plugin.getLogger().info(String.format("Enabled script %s in %dms", scriptFile.getName(), duration));
            
            return true;
        
        } catch (Exception e) {
            String errorMsg = "Failed to initialize script: " + scriptFile.getName();
            plugin.getLogger().log(Level.SEVERE, errorMsg, e);
            
            // Update error state
            errorCount.incrementAndGet();
            state.set(ScriptState.ERROR);
            
            // Try to clean up partially initialized resources
            safeClose();
            
            throw new IllegalStateException(errorMsg, e);
            
        } finally {
            isInitializing.set(false);
        }
    }
    
    /**
     * Creates a secure GraalVM context with appropriate security settings.
     */
    private Context createContext() {
        // Configure host access policies
        HostAccess hostAccess = HostAccess.newBuilder()
            .allowPublicAccess(true)
            .allowArrayAccess(true)
            .allowListAccess(true)
            .allowMapAccess(true)
            .allowBufferAccess(true)
            .allowIterableAccess(true)
            .allowIteratorAccess(true)
            .allowMapAccess(true)
            .build();
            
        // Create a new context builder with the current file system access
        return Context.newBuilder()
                .allowIO(true)
                .allowNativeAccess(false)
                .allowCreateThread(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> {
                    try {
                        return securityManager.isClassAllowed(className);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error checking if class is allowed: " + className, e);
                        return false;
                    }
                })
                .fileSystem(FileSystem.newDefaultFileSystem())
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }
    
    /**
     * Loads and executes the script content.
     */
    private void loadAndExecuteScript() throws IOException {
        // Read script content with proper error handling
        String scriptContent;
        try {
            scriptContent = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to read script file: " + e.getMessage(), e);
        }
        
        // Create source with proper MIME type
        Source source = Source.newBuilder(language.getGraalId(), scriptContent, scriptFile.getName())
            .mimeType(language.getMimeType())
            .build();
            
        try {
            // Execute the script
            context.eval(source);
            
            // Call the onEnable function if it exists
            safeCallScriptFunction("onEnable");
        } catch (Exception e) {
            // Command might not be registered or already unregistered
            plugin.getLogger().log(Level.WARNING, "Error in script execution: " + e.getMessage(), e);
        }
    }
    
    private void setupPolyAPI() {
        // Add utility functions to the poly object
        polyObject.putMember("log", (ProxyExecutable) args -> {
            if (args.length > 0) {
                plugin.getLogger().info("[" + scriptFile.getName() + "] " + args[0].toString());
            }
            return null;
        });
        
        polyObject.putMember("warn", (ProxyExecutable) args -> {
            if (args.length > 0) {
                plugin.getLogger().warning("[" + scriptFile.getName() + "] " + args[0].toString());
            }
            return null;
        });
        
        // Add event registration
        polyObject.putMember("on", (ProxyExecutable) this::registerEvent);
        
        // Add command registration
        polyObject.putMember("registerCommand", (ProxyExecutable) this::registerCommand);
        
        // Add scheduler utilities
        Value schedulerObj = context.eval(language.getGraalId(), "({})");
        schedulerObj.putMember("runAsync", (ProxyExecutable) this::scheduleAsyncTask);
        schedulerObj.putMember("runSync", (ProxyExecutable) this::scheduleSyncTask);
        schedulerObj.putMember("runTimer", (ProxyExecutable) this::scheduleTimerTask);
        schedulerObj.putMember("cancel", (ProxyExecutable) this::cancelScheduledTask);
        polyObject.putMember("scheduler", schedulerObj);
        
        // Add configuration
        Value configObj = context.eval(language.getGraalId(), "({})");
        configObj.putMember("get", (ProxyExecutable) this::getConfigValue);
        configObj.putMember("set", (ProxyExecutable) this::setConfigValue);
        configObj.putMember("save", (ProxyExecutable) this::saveConfig);
        polyObject.putMember("config", configObj);
        
        // Add data storage
        Value dataObj = context.eval(language.getGraalId(), "({})");
        dataObj.putMember("get", (ProxyExecutable) this::getDataValue);
        dataObj.putMember("set", (ProxyExecutable) this::setDataValue);
        dataObj.putMember("save", (ProxyExecutable) this::saveData);
        polyObject.putMember("data", dataObj);
        
        // Add utility functions
        polyObject.putMember("getServer", (ProxyExecutable) args -> plugin.getServer());
        polyObject.putMember("getPlugin", (ProxyExecutable) args -> plugin);
        
        // Add player utilities
        Value playerUtils = context.eval(language.getGraalId(), "({})");
        playerUtils.putMember("get", (ProxyExecutable) this::getPlayer);
        playerUtils.putMember("getOnline", (ProxyExecutable) this::getOnlinePlayers);
        polyObject.putMember("player", playerUtils);
    }
    
    private Object getPlayer(Value... args) {
        if (args.length > 0) {
            String playerName = args[0].asString();
            return plugin.getServer().getPlayer(playerName);
        }
        return null;
    }
    
    private Object getOnlinePlayers(Value... args) {
        return new ArrayList<>(plugin.getServer().getOnlinePlayers());
    }
    
    // Scheduler methods
    private Object scheduleAsyncTask(Value... args) {
        if (args.length >= 1 && args[0].canExecute()) {
            Runnable task = () -> args[0].executeVoid();
            long delay = args.length > 1 ? args[1].asLong() : 0;
            return scheduler.runAsync(this, task, delay);
        }
        return null;
    }
    
    private Object scheduleSyncTask(Value... args) {
        if (args.length >= 1 && args[0].canExecute()) {
            Runnable task = () -> args[0].executeVoid();
            long delay = args.length > 1 ? args[1].asLong() : 0;
            return scheduler.runSync(this, task, delay);
        }
        return null;
    }
    
    private Object scheduleTimerTask(Value... args) {
        if (args.length >= 3 && args[0].canExecute()) {
            Runnable task = () -> args[0].executeVoid();
            long delay = args[1].asLong();
            long period = args[2].asLong();
            boolean async = args.length > 3 && args[3].asBoolean();
            
            if (async) {
                return scheduler.runAsyncTimer(this, task, delay, period);
            } else {
                return scheduler.runSyncTimer(this, task, delay, period);
            }
        }
        return null;
    }
    
    private Object cancelScheduledTask(Value... args) {
        if (args.length >= 1) {
            try {
                UUID taskId = UUID.fromString(args[0].asString());
                scheduler.cancelTask(taskId);
                return true;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid task ID: " + args[0].asString());
            }
        }
        return false;
    }
    
    // Configuration methods
    private Object getConfigValue(Value... args) {
        if (args.length >= 1) {
            String key = args[0].asString();
            if (args.length > 1) {
                return config.get(key, args[1].as(Object.class));
            }
            return config.get(key);
        }
        return null;
    }
    
    private Object setConfigValue(Value... args) {
        if (args.length >= 2) {
            String key = args[0].asString();
            Object value = args[1].as(Object.class);
            config.set(key, value);
            return true;
        }
        return false;
    }
    
    private Object saveConfig(Value... args) {
        config.saveConfig();
        return true;
    }
    
    // Data storage methods
    private Object getDataValue(Value... args) {
        if (args.length >= 1) {
            String key = args[0].asString();
            FileConfiguration data = dataManager.getScriptData(this);
            if (args.length > 1) {
                return data.get(key, args[1].as(Object.class));
            }
            return data.get(key);
        }
        return null;
    }
    
    private Object setDataValue(Value... args) {
        if (args.length >= 2) {
            String key = args[0].asString();
            Object value = args[1].as(Object.class);
            FileConfiguration data = dataManager.getScriptData(this);
            data.set(key, value);
            dataManager.saveScriptData(this);
            return true;
        }
        return false;
    }
    
    private Object saveData(Value... args) {
        dataManager.saveScriptData(this);
        return true;
    }
    
    private Object registerCommand(Value... args) {
        if (args.length >= 2) {
            String command = args[0].asString();
            Value handler = args[1];
            
            if (handler.canExecute()) {
                registeredCommands.put(command.toLowerCase(), handler);
                return true;
            }
        }
        return false;
    }
    
    @SuppressWarnings("unchecked")
    private Object registerEvent(Value... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("registerEvent requires at least 2 arguments (eventName, handler)");
        }
        
        String eventName = args[0].asString();
        Value handler = args[1];
        
        if (handler == null || !handler.canExecute()) {
            return false;
        }
        
        try {
            // Find the event class
            Class<? extends Event> eventClass = null;
            try {
                // First try the Bukkit event
                eventClass = (Class<? extends Event>) Class.forName("org.bukkit.event." + eventName);
            } catch (ClassNotFoundException e) {
                // Try with the full class name
                try {
                    eventClass = (Class<? extends Event>) Class.forName(eventName);
                } catch (ClassNotFoundException ex) {
                    plugin.getLogger().warning("Unknown event: " + eventName);
                    return false;
                }
            }
            
            // Register with the event manager using the handler function name
            eventManager.registerEvent(eventClass, this, eventName);
            
            // Create the event handler for internal tracking
            ScriptEventHandler eventHandler = new ScriptEventHandler(this, eventClass, eventName, handler);
            
            // Store the handler for cleanup
            eventHandlers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(eventHandler);
            
            // Also track the event class for unregistration
            registeredEvents.computeIfAbsent(eventClass, k -> ConcurrentHashMap.newKeySet()).add(eventName);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error registering event: " + eventName, e);
            return false;
        }
    }
    
    /**
     * Unregisters an event handler.
     * @param handler The event handler to unregister
     * @return true if the event was unregistered, false otherwise
     */
    public boolean unregisterEvent(ScriptEventHandler handler) {
        if (handler == null) {
            return false;
        }
        
        try {
            // Remove from our tracking
            eventHandlers.computeIfPresent(handler.getEventName(), (k, list) -> {
                list.remove(handler);
                return list.isEmpty() ? null : list;
            });
            
            // Unregister from the event manager
            // The EventManager only has a method to unregister all events for a script
            // So we'll need to unregister all events and then re-register the remaining ones
            // This is not ideal but works with the current EventManager implementation
            eventManager.unregisterEvents(this);
            
            // Remove the unregistered handler from our tracking
            List<ScriptEventHandler> handlers = eventHandlers.get(handler.getEventName());
            if (handlers != null) {
                handlers.remove(handler);
                if (handlers.isEmpty()) {
                    eventHandlers.remove(handler.getEventName());
                }
            }
            
            // Re-register remaining handlers for this event class
            registeredEvents.computeIfPresent(handler.getEventClass(), (key, set) -> {
                set.remove(handler.getHandlerName());
                return set.isEmpty() ? null : set;
            });
            
            // Re-register remaining handlers for this event class
            for (Map.Entry<String, List<ScriptEventHandler>> entry : eventHandlers.entrySet()) {
                for (ScriptEventHandler h : entry.getValue()) {
                    if (h.getEventClass().equals(handler.getEventClass())) {
                        // Register the event with the script instance and handler name
                        eventManager.registerEvent(h.getEventClass(), this, entry.getKey());
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error unregistering event handler", e);
            return false;
        }
    }
    
    /**
     * Unregisters an event by name.
     * @param args The event name and optional handler function
     * @return true if the event was unregistered, false otherwise
     */
    private Object unregisterEvent(Value... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("unregisterEvent requires at least 1 argument (eventName)");
        }
        
        String eventName = args[0].asString();
        if (eventName == null) {
            return false;
        }
        
        try {
            // Find the event class
            Class<? extends Event> eventClass = null;
            try {
                // First try the Bukkit event
                eventClass = (Class<? extends Event>) Class.forName("org.bukkit.event." + eventName);
            } catch (ClassNotFoundException e) {
                // Try with the full class name
                try {
                    eventClass = (Class<? extends Event>) Class.forName(eventName);
                } catch (ClassNotFoundException ex) {
                    // If we can't find the class, still try to unregister by name
                    if (eventHandlers.containsKey(eventName)) {
                        eventHandlers.remove(eventName);
                        return true;
                    }
                    plugin.getLogger().warning("Unknown event: " + eventName);
                    return false;
                }
            }
            
            // Get all handlers for this event
            List<ScriptEventHandler> handlers = eventHandlers.get(eventName);
            if (handlers != null && !handlers.isEmpty()) {
                // If a specific handler function was provided, only remove that one
                if (args.length > 1 && args[1] != null) {
                    String handlerName = args[1].getMember("name").asString();
                    if (handlerName != null) {
                        boolean removed = handlers.removeIf(handler -> 
                            handler.getHandlerName() != null && 
                            handler.getHandlerName().equals(handlerName)
                        );
                        
                        if (handlers.isEmpty()) {
                            eventHandlers.remove(eventName);
                            registeredEvents.remove(eventClass);
                        }
                        return removed;
                    }
                }
                
                // Remove all handlers for this event
                // The EventManager will handle the cleanup through scriptEvents tracking
                eventManager.unregisterEvents(this);
                
                // Remove from our tracking
                eventHandlers.remove(eventName);
                
                // Also remove from registered events
                registeredEvents.remove(eventClass);
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error unregistering event: " + eventName, e);
            return false;
        }
    }
    
    /**
     * Unregisters all event handlers.
     */
    public void unregisterAllEventHandlers() {
        // Let the event manager handle cleanup of all events for this script
        eventManager.unregisterEvents(this);
        
        // Clear our local tracking
        eventHandlers.clear();
        registeredEvents.clear();
    }
    
    /**
     * Calls a script function with the given arguments.
     * @param function The function to call
     * @param args The arguments to pass to the function
     * @return The result of the function call, or null if an error occurred
     */
    /**
     * Gets the logger instance for this script.
     * @return The logger instance
     */
    public Logger getLogger() {
        return plugin.getLogger();
    }
    
    /**
     * Safely calls a script function with the given name and arguments.
     * @param functionName The name of the function to call
     * @param args The arguments to pass to the function
     * @return The result of the function call, or null if the function doesn't exist or an error occurs
     */
    public Object safeCallScriptFunction(String functionName, Object... args) {
        if (functionName == null || functionName.isEmpty() || context == null) {
            return null;
        }
        
        try {
            Value function = context.getBindings("js").getMember(functionName);
            if (function == null || !function.canExecute()) {
                return null;
            }
            return function.execute(args);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, 
                String.format("Error executing script function '%s' in %s", 
                    functionName, scriptFile.getName()), e);
            return null;
        }
    }
    
    /**
     * Calls a script function with the given function object and arguments.
     * @param function The function to call
     * @param args The arguments to pass to the function
     * @return The result of the function call, or null if an error occurred
     */
    public Object callScriptFunction(Value function, Object... args) {
        if (function == null || !function.canExecute()) {
            return null;
        }
        
        try {
            return function.execute(args);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, 
                "Error executing script function in " + scriptFile.getName(), e);
            return null;
        }
    }
    
    /**
     * Disables the script, cleaning up all resources.
     * @return true if the script was successfully disabled, false otherwise
     */
    public boolean disable() {
        if (isDisabling.getAndSet(true)) {
            return false; // Already disabling
        }
        
        try {
            // Unregister all event handlers
            unregisterAllEventHandlers();
            
            // Close all resources
            safeClose();
            
            // Update state
            enabled = false;
            state.set(ScriptState.DISABLED);
            lastDisabledTime = Instant.now();
            
            getLogger().info("Script disabled: " + scriptFile.getName());
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error disabling script: " + scriptFile.getName(), e);
            state.set(ScriptState.ERROR);
            return false;
        } finally {
            isDisabling.set(false);
        }
    }
    
    /**
     * Checks if the script is currently enabled.
     * @return true if the script is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if this script has a specific permission.
     * @param permission The permission to check
     * @return true if the script has the permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        // Delegate to the security manager
        return securityManager.hasPermission(this, permission);
    }
    
    /**
     * Calls an event handler in the script.
     * @param handlerName The name of the handler function
     * @param event The event to pass to the handler
     */
    public void callEvent(String handlerName, Event event) {
        if (handlerName == null || handlerName.isEmpty() || event == null) {
            return;
        }
        
        try {
            // Check if the script is enabled
            if (!isEnabled()) {
                return;
            }
            
            // Get the handler function from the script
            Value handler = context.getBindings(language.getGraalId()).getMember(handlerName);
            if (handler == null || !handler.canExecute()) {
                // Handler doesn't exist or is not callable
                return;
            }
            
            // Call the handler with the event
            handler.execute(event);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error calling event handler: " + handlerName, e);
        }
    }
    
    /**
     * Gets the script file associated with this instance.
     * @return The script file
     */
    public File getScriptFile() {
        return scriptFile;
    }
    
    /**
     * Gets the script configuration.
     * @return The script configuration
     */
    public ScriptConfig getConfig() {
        return config;
    }
    

    /**
     * Gets the last modified timestamp of the script file.
     * @return The last modified timestamp in milliseconds since epoch
     */
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Closes all registered resources.
     */
    private void closeAllResources() {
        // Close resources in reverse order of registration
        List<AutoCloseable> resourcesToClose = new ArrayList<>(resources);
        Collections.reverse(resourcesToClose);
        
        for (AutoCloseable resource : resourcesToClose) {
            try {
                resource.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Error closing resource for script: " + scriptFile.getName(), e);
            }
        }
        resources.clear();
    }
    
    /**
     * Closes the GraalVM context if it exists.
     */
    private void closeContext() {
        if (context != null) {
            try {
                context.close(true); // Interrupt any running executions
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, 
                    "Error closing script context: " + scriptFile.getName(), e);
            } finally {
                context = null;
                bindings = null;
                polyObject = null;
            }
        }
    }
    
    /**
     * Shuts down an executor service and waits for termination.
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        executor.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                // Cancel currently executing tasks
                executor.shutdownNow();
                // Wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning(
                        String.format("%s did not terminate for script: %s", 
                            name, scriptFile.getName()));
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void close() {
        try {
            if (enabled) {
                disable();
            } else {
                safeClose();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while closing script " + scriptFile.getName(), e);
        }
    }
    
    private void safeClose() {
        com.google.common.base.Stopwatch timer = com.google.common.base.Stopwatch.createStarted();
        try {
            // Close the context first to prevent new operations
            closeContext();
            
            // Close all registered resources
            closeAllResources();
            
            // Shutdown async executor
            shutdownExecutor(asyncExecutor, "async executor");
            
            // Clear all collections
            scriptData.clear();
            eventHandlers.clear();
            registeredEvents.clear();
            registeredCommands.clear();
            threadCpuStartTimes.clear();
            
            // Update state
            enabled = false;
            state.set(ScriptState.DISABLED);
            
            long elapsed = timer.elapsed(TimeUnit.MILLISECONDS);
            plugin.getLogger().info("Script executed in " + elapsed + "ms: " + scriptFile.getName());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, 
                "Error during safe close of script: " + scriptFile.getName(), e);
            state.set(ScriptState.ERROR);
        } finally {
            timer.stop();
        }
    }
    
    public enum ScriptLanguage {
        JAVASCRIPT("js", "application/javascript"),
        PYTHON("python", "application/python"),
        JAVA("java", "text/x-java-source"),
        CSHARP("cs", "text/x-csharp");
        
        private static final Map<String, ScriptLanguage> EXTENSION_MAP = new HashMap<>();
        private static final Map<String, String> MIME_TYPES = new HashMap<>();
        
        static {
            for (ScriptLanguage lang : values()) {
                EXTENSION_MAP.put("." + lang.getGraalId(), lang);
                MIME_TYPES.put(lang.getGraalId(), lang.getMimeType());
            }
            // Add additional file extensions
            EXTENSION_MAP.put(".js", JAVASCRIPT);
            EXTENSION_MAP.put(".py", PYTHON);
            EXTENSION_MAP.put(".java", JAVA);
            EXTENSION_MAP.put(".cs", CSHARP);
        }
        
        private final String graalId;
        private final String mimeType;
        
        ScriptLanguage(String graalId, String mimeType) {
            this.graalId = graalId;
            this.mimeType = mimeType;
        }
        
        public String getGraalId() {
            return graalId;
        }
        
        /**
         * Gets the MIME type associated with this script language.
         * @return The MIME type as a string
         */
        public String getMimeType() {
            return mimeType;
        }
        
        /**
         * Determines the script language from a file name based on its extension.
         * @param fileName The name of the file
         * @return The ScriptLanguage corresponding to the file extension, or null if unknown
         */
        public static ScriptLanguage fromFileName(String fileName) {
            if (fileName == null) {
                return null;
            }
            
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot == -1) {
                return null;
            }
            
            String extension = fileName.substring(lastDot).toLowerCase();
            return EXTENSION_MAP.get(extension);
        }
    }
}
