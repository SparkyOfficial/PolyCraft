package com.polycraft.engine;

import com.polycraft.engine.api.PolyAPI;
import com.polycraft.engine.commands.PolyCraftCommand;
import com.polycraft.engine.config.ScriptConfig;
import com.polycraft.engine.data.ScriptDataManager;
import com.polycraft.engine.listeners.EventManager;
import com.polycraft.engine.scripting.ScriptManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;


import java.util.logging.Level;

public final class PolyCraftEngine extends JavaPlugin {
    
    private static PolyCraftEngine instance;
    
    // Core components
    private ScriptManager scriptManager;
    private EventManager eventManager;
    private ScriptConfig scriptConfig;
    private ScriptDataManager dataManager;
    private PolyAPI polyAPI;
    
    // GraalVM components
    private Context polyglotContext;
    private Engine graalEngine;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize logger
        getLogger().info("Initializing PolyCraft Engine " + getDescription().getVersion());
        
        try {
            // Create scripts directory if it doesn't exist
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            
            // Initialize GraalVM
            initializeGraalVM();
            
            // Initialize configuration and data
            this.scriptConfig = new ScriptConfig(this);
            this.dataManager = new ScriptDataManager(this);
            this.polyAPI = new PolyAPI(this);
            
            // Initialize managers
            this.eventManager = new EventManager(this);
            this.scriptManager = new ScriptManager(this);
            
            // Register commands
            getCommand("polycraft").setExecutor(new PolyCraftCommand(this));
            
            // Register events
            getServer().getPluginManager().registerEvents(eventManager, this);
            
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            getLogger().info("PolyCraft Engine has been enabled!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize PolyCraft Engine", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        // Cleanup resources
        if (scriptManager != null) {
            scriptManager.shutdown();
        }
        
        // Save all data
        if (dataManager != null) {
            dataManager.saveAll();
        }
        
        // Close GraalVM context
        if (polyglotContext != null) {
            try {
                polyglotContext.close();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error closing GraalVM context", e);
            }
        }
        
        getLogger().info("PolyCraft Engine has been disabled!");
    }
    
    private void initializeGraalVM() {
        try {
            // Enable all languages explicitly
            System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
            
            // Create a shared GraalVM engine with explicit language options
            graalEngine = Engine.newBuilder()
                    .useSystemProperties(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .option("js.ecmascript-version", "2022")
                    .option("js.nashorn-compat", "true")
                    .option("js.commonjs-require", "true")
                    .option("js.commonjs-require-cwd", getDataFolder().getAbsolutePath() + "/scripts")
                    .build();
            
            // Verify that languages are available
            if (!graalEngine.getLanguages().containsKey("js")) {
                throw new IllegalStateException("JavaScript language is not available. Check your GraalVM installation.");
            }
            
            // Create a new GraalVM context with basic permissions
            polyglotContext = Context.newBuilder()
                    .engine(graalEngine)
                    .allowAllAccess(true)
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(className -> true)
                    .allowCreateThread(true)
                    .build();
            
            getLogger().info("GraalVM Polyglot Engine initialized with languages: " + graalEngine.getLanguages().keySet());
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize GraalVM: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("GraalVM initialization failed", e);
        }
    }
    
    /**
     * Gets the plugin instance.
     * @return The plugin instance
     */
    public static PolyCraftEngine getInstance() {
        return instance;
    }
    
    /**
     * Gets the script manager instance.
     * @return The script manager
     */
    public ScriptManager getScriptManager() {
        return scriptManager;
    }
    
    /**
     * Gets the event manager instance.
     * @return The event manager
     */
    public EventManager getEventManager() {
        return eventManager;
    }
    
    /**
     * Gets the script configuration manager.
     * @return The script configuration manager
     */
    public ScriptConfig getScriptConfig() {
        return scriptConfig;
    }
    
    /**
     * Gets the data manager.
     * @return The data manager
     */
    public ScriptDataManager getDataManager() {
        return dataManager;
    }
    
    /**
     * Gets the PolyAPI instance.
     * @return The PolyAPI instance
     */
    public PolyAPI getPolyAPI() {
        return polyAPI;
    }
    
    /**
     * Gets the GraalVM engine instance.
     * @return The GraalVM engine
     */
    public Engine getGraalEngine() {
        return graalEngine;
    }
    
    /**
     * Gets the Polyglot context.
     * @return The Polyglot context
     */
    public Context getPolyglotContext() {
        return polyglotContext;
    }
}
