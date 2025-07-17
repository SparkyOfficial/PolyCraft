package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.api.PolyAPI;
import com.polycraft.engine.commands.ScriptCommandManager;
import com.polycraft.engine.config.ScriptConfigManager;
import com.polycraft.engine.data.ScriptDataManager;
import com.polycraft.engine.events.ScriptEventManager;
import com.polycraft.engine.scheduler.ScriptScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Handles script execution and lifecycle management.
 */
public class ScriptExecutor implements AutoCloseable {
    
    private final PolyCraftEngine plugin;
    private final ScriptMetadata metadata;
    private final ScriptLanguage language;
    private final File scriptFile;
    private final File dataFolder;
    private final ScriptConfigManager configManager;
    private final ScriptDataManager dataManager;
    private final ScriptScheduler scheduler;
    private final ScriptEventManager eventManager;
    private final ScriptCommandManager commandManager;
    private final DependencyManager dependencyManager;
    private final PolyAPI api;
    
    private Context context;
    private boolean initialized = false;
    private boolean enabled = false;
    private Throwable lastError = null;
    private final Map<String, Object> bindings = new ConcurrentHashMap<>();
    private final List<AutoCloseable> resources = new ArrayList<>();
    
    public ScriptExecutor(
            PolyCraftEngine plugin,
            ScriptMetadata metadata,
            ScriptConfigManager configManager,
            ScriptDataManager dataManager,
            ScriptScheduler scheduler,
            ScriptEventManager eventManager,
            ScriptCommandManager commandManager,
            DependencyManager dependencyManager) {
        
        this.plugin = plugin;
        this.metadata = metadata;
        this.language = metadata.getLanguage();
        this.scriptFile = metadata.getScriptFile();
        this.dataFolder = metadata.getDataFolder();
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.scheduler = scheduler;
        this.eventManager = eventManager;
        this.commandManager = commandManager;
        this.dependencyManager = dependencyManager;
        this.api = new PolyAPI(plugin);
    }
    
    /**
     * Initialize the script executor.
     */
    public boolean initialize() {
        if (initialized) {
            return true;
        }
        
        try {
            // Create data folder if it doesn't exist
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            // Initialize GraalVM context
            initializeContext();
            
            // Set up default bindings
            initializeBindings();
            
            // Load script source
            Source source = loadScriptSource();
            if (source == null) {
                throw new IOException("Failed to load script source: " + scriptFile.getName());
            }
            
            // Execute the script
            context.eval(source);
            
            // Call the onEnable function if it exists
            callScriptFunction("onEnable");
            
            initialized = true;
            enabled = true;
            lastError = null;
            
            plugin.getLogger().info("Script loaded: " + metadata.getName() + " v" + metadata.getVersion());
            return true;
            
        } catch (Throwable t) {
            lastError = t;
            plugin.getLogger().log(Level.SEVERE, "Error initializing script: " + metadata.getName(), t);
            
            // Clean up resources
            try {
                close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error cleaning up after failed initialization", e);
            }
            
            return false;
        }
    }
    
    /**
     * Initialize the GraalVM context.
     */
    private void initializeContext() {
        // Configure context with appropriate options
        Context.Builder builder = Context.newBuilder(language.getGraalId())
            .allowAllAccess(true) // TODO: Implement proper sandboxing
            .allowIO(true)
            .allowHostAccess(HostAccess.ALL)
            .allowExperimentalOptions(true)
            .option("engine.WarnInterpreterOnly", "false")
            .option("js.ecmascript-version", "2022")
            .option("python.ForceImportSite", "true")
            .option("python.PosixModule", "native")
            .option("python.Executable", "python3") // TODO: Make configurable
            .option("ruby.platform.native.enabled", "true")
            .option("ruby.platform.native.library.paths", "/usr/local/lib:/usr/lib:/lib");
        
        // Create the context
        context = builder.build();
        resources.add(context);
    }
    
    /**
     * Initialize the default bindings for the script context.
     */
    private void initializeBindings() {
        // Basic bindings
        bindings.put("__polycraft__", api);
        bindings.put("console", plugin.getServer().getConsoleSender());
        
        // API modules - these will be exposed through the API object
        bindings.put("events", api);
        bindings.put("scheduler", api);
        bindings.put("config", api);
        bindings.put("data", api);
        bindings.put("commands", api);
        bindings.put("utils", api);
        
        // Add bindings to the context
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            context.getBindings(language.getGraalId()).putMember(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Load the script source from file.
     */
    private Source loadScriptSource() throws IOException {
        try {
            return Source.newBuilder(language.getGraalId(), scriptFile)
                    .name(scriptFile.getName())
                    .mimeType(language.getMimeType())
                    .build();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load script source: " + scriptFile.getName(), e);
            throw e;
        }
    }
    
    /**
     * Call a function in the script.
     */
    public Object callScriptFunction(String name, Object... args) {
        if (!initialized || !enabled) {
            return null;
        }
        
        try {
            Value function = context.getBindings(language.getGraalId()).getMember(name);
            if (function != null && function.canExecute()) {
                return function.execute(args);
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error calling function " + name + " in script: " + metadata.getName(), e);
            lastError = e;
            return null;
        }
    }
    
    /**
     * Handle the 'require' function for script dependencies.
     */
    private Object requireScript(Value... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("require() expects at least one argument");
        }
        
        String moduleName = args[0].asString();
        
        try {
            // Try to resolve as a built-in module first
            switch (moduleName) {
                case "events":
                    return eventManager;
                case "scheduler":
                    return scheduler;
                case "config":
                    return configManager;
                case "data":
                    return dataManager;
                case "commands":
                    return commandManager;
                case "utils":
                    // Return a simple utils object with common utilities
                    Map<String, Object> utils = new HashMap<>();
                    utils.put("formatLocation", (java.util.function.Function<org.bukkit.Location, String>) 
                        location -> String.format("%s: %d, %d, %d", 
                            location.getWorld().getName(),
                            location.getBlockX(),
                            location.getBlockY(),
                            location.getBlockZ()
                        )
                    );
                    return utils;
            }
            
            // Try to resolve as a custom module
            File moduleFile = new File(scriptFile.getParentFile(), moduleName + ".js");
            if (moduleFile.exists()) {
                try {
                    Source source = Source.newBuilder("js", moduleFile).build();
                    return context.eval(source);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load custom module: " + moduleName, e);
                }
            }
            
            throw new UnsupportedOperationException("Module not found: " + moduleName);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to require module: " + moduleName, e);
        }
    }
    
    /**
     * Handle logging from scripts.
     */
    private Object logMessage(Value... args) {
        StringBuilder message = new StringBuilder("[" + metadata.getName() + "] ");
        for (Value arg : args) {
            message.append(arg.toString()).append(" ");
        }
        plugin.getLogger().info(message.toString().trim());
        return null;
    }
    
    /**
     * Enable the script.
     */
    public boolean enable() {
        if (enabled) {
            return true;
        }
        
        try {
            callScriptFunction("onEnable");
            enabled = true;
            lastError = null;
            return true;
        } catch (Exception e) {
            lastError = e;
            plugin.getLogger().log(Level.SEVERE, "Error enabling script: " + metadata.getName(), e);
            return false;
        }
    }
    
    /**
     * Disable the script.
     */
    public boolean disable() {
        if (!enabled) {
            return true;
        }
        
        try {
            callScriptFunction("onDisable");
            enabled = false;
            return true;
        } catch (Exception e) {
            lastError = e;
            plugin.getLogger().log(Level.SEVERE, "Error disabling script: " + metadata.getName(), e);
            return false;
        } finally {
            // Clean up resources
            try {
                close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error cleaning up script resources", e);
            }
        }
    }
    
    /**
     * Reload the script.
     */
    public boolean reload() {
        try {
            // Disable first if enabled
            if (enabled) {
                disable();
            }
            
            // Clear context and reinitialize
            close();
            initialized = false;
            
            return initialize();
        } catch (Exception e) {
            lastError = e;
            plugin.getLogger().log(Level.SEVERE, "Error reloading script: " + metadata.getName(), e);
            return false;
        }
    }
    
    /**
     * Get the script metadata.
     */
    public ScriptMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Get the script language.
     */
    public ScriptLanguage getLanguage() {
        return language;
    }
    
    /**
     * Get the script file.
     */
    public File getScriptFile() {
        return scriptFile;
    }
    
    /**
     * Get the data folder for this script.
     */
    public File getDataFolder() {
        return dataFolder;
    }
    
    /**
     * Check if the script is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the last error that occurred, if any.
     */
    public Throwable getLastError() {
        return lastError;
    }
    
    /**
     * Get a binding value.
     */
    public Object getBinding(String name) {
        return bindings.get(name);
    }
    
    /**
     * Set a binding value.
     */
    public void setBinding(String name, Object value) {
        bindings.put(name, value);
        if (initialized) {
            context.getBindings(language.getGraalId()).putMember(name, value);
        }
    }
    
    /**
     * Register a resource to be closed when the script is disabled.
     */
    public void registerResource(AutoCloseable resource) {
        resources.add(resource);
    }
    
    @Override
    public void close() {
        // Close resources in reverse order
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing script resource", e);
            }
        }
        resources.clear();
        
        // Clear context
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing script context", e);
            }
            context = null;
        }
        
        // Clear state
        initialized = false;
        enabled = false;
        bindings.clear();
    }
}
