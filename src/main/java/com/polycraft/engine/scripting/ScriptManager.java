package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.config.ScriptConfig;
import com.polycraft.engine.data.ScriptDataManager;
import com.polycraft.engine.listeners.EventManager;
import com.polycraft.engine.scheduler.ScriptScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.io.MessageTransport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

public class ScriptManager {
    
    private final PolyCraftEngine plugin;
    private final Map<String, ScriptInstance> loadedScripts = new ConcurrentHashMap<>();
    private final ScriptLoader scriptLoader;
    private WatchService watchService;
    private Thread watchThread;
    private boolean watching = false;
    private final File scriptsFolder;
    private final File configFile;
    private FileConfiguration scriptConfig;
    
    private final ScriptScheduler scriptScheduler;
    
    public ScriptManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.scriptLoader = new ScriptLoader(plugin);
        this.scriptsFolder = new File(plugin.getDataFolder(), "scripts");
        this.configFile = new File(plugin.getDataFolder(), "scripts.yml");
        this.scriptScheduler = new ScriptScheduler(plugin);
        
        // Create scripts folder if it doesn't exist
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();
        }
        
        // Load script configuration
        loadScriptConfig();
        
        // Start watching for file changes
        startWatching();
        
        // Log supported languages
        logSupportedLanguages();
    }
    
    /**
     * Reloads a specific script by its name.
     * @param scriptName The name of the script to reload
     * @return true if the script was successfully reloaded, false otherwise
     */
    public boolean reloadScript(String scriptName) {
        if (scriptName == null || scriptName.trim().isEmpty()) {
            throw new IllegalArgumentException("Script name cannot be null or empty");
        }
        
        ScriptInstance script = loadedScripts.get(scriptName);
        if (script == null) {
            plugin.getLogger().warning("Cannot reload script '" + scriptName + "': Not loaded");
            return false;
        }
        
        try {
            // Get the script file before disabling
            File scriptFile = script.getScriptFile();
            
            // Unload the script first
            unloadScript(scriptName);
            
            // Load the script again
            return loadScript(scriptFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error reloading script: " + scriptName, e);
            return false;
        }
    }
    
    /**
     * Reloads all currently loaded scripts.
     * @return The number of scripts that were successfully reloaded
     */
    public int reloadAllScripts() {
        int successCount = 0;
        List<String> scriptNames = new ArrayList<>(loadedScripts.keySet());
        
        for (String scriptName : scriptNames) {
            if (reloadScript(scriptName)) {
                successCount++;
            }
        }
        
        plugin.getLogger().info("Reloaded " + successCount + "/" + scriptNames.size() + " scripts");
        return successCount;
    }
    
    /**
     * Evaluates a code snippet in the specified language.
     * @param language The language of the code
     * @param code The code to evaluate
     * @return The result of the evaluation, or null if an error occurred
     */
    public Object evaluateCode(String language, String code) {
        if (language == null || code == null || code.trim().isEmpty()) {
            return null;
        }
        
        try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder()
                .allowAllAccess(false)
                .allowIO(true)  // Allow IO but with default restrictions
                .build()) {
            return context.eval(language, code);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error evaluating code", e);
            return null;
        }
    }
    
    private void loadScriptConfig() {
        scriptConfig = YamlConfiguration.loadConfiguration(configFile);
    }
    
    private void logSupportedLanguages() {
        // Log the available script languages
        String languages = String.join(", ", 
            ScriptLanguage.JAVASCRIPT.getGraalId(),
            ScriptLanguage.PYTHON.getGraalId(),
            ScriptLanguage.JAVA.getGraalId(),
            ScriptLanguage.CSHARP.getGraalId()
        );
        plugin.getLogger().info("Available script languages: " + languages);
    }
    
    public void loadScripts() {
        List<File> scriptFiles = scriptLoader.listScripts();
        
        if (scriptFiles.isEmpty()) {
            plugin.getLogger().info("No script files found in " + scriptLoader.getScriptsFolder().getPath());
            return;
        }
        
        // First, load all script configs
        Map<File, ScriptLoader.ScriptConfig> configs = new HashMap<>();
        for (File scriptFile : scriptFiles) {
            ScriptLoader.ScriptConfig config = scriptLoader.getScriptConfig(scriptFile);
            if (config != null) {
                configs.put(scriptFile, config);
            }
        }
        
        // Then load scripts in dependency order
        Set<File> loaded = new HashSet<>();
        for (File scriptFile : scriptFiles) {
            if (!loaded.contains(scriptFile)) {
                loadScriptWithDependencies(scriptFile, configs, loaded);
            }
        }
    }
    
    private void loadScriptWithDependencies(File scriptFile, Map<File, ScriptLoader.ScriptConfig> configs, Set<File> loaded) {
        // Already loaded
        if (loaded.contains(scriptFile)) {
            return;
        }
        
        // Mark as visited to detect circular dependencies
        loaded.add(scriptFile);
        
        // Load dependencies first
        ScriptLoader.ScriptConfig config = configs.get(scriptFile);
        if (config != null) {
            for (String dep : config.getDependencies()) {
                File depFile = scriptLoader.findDependency(scriptFile.getParentFile(), dep);
                if (depFile != null && !loaded.contains(depFile)) {
                    loadScriptWithDependencies(depFile, configs, loaded);
                }
            }
        }
        
        // Load the script
        loadScript(scriptFile);
    }
    
    public boolean loadScript(File scriptFile) {
        String scriptName = scriptFile.getName();
        
        // Unload first if already loaded
        if (loadedScripts.containsKey(scriptName)) {
            unloadScript(scriptName);
        }
        
        try {
            // Create required components
            EventManager eventManager = new EventManager(plugin);
            ScriptConfig config = new ScriptConfig(plugin);
            ScriptDataManager dataManager = new ScriptDataManager(plugin);
            
            // Create script instance with all required dependencies
            ScriptInstance script = new ScriptInstance(
                plugin, 
                scriptFile, 
                scriptScheduler,
                eventManager,
                config,
                dataManager
            );
            
            // Configure the script
            // The config and data manager are already initialized in the ScriptInstance constructor
            // No need to call load() here as it's handled internally
            
            // Initialize the script
            if (script.initialize()) {
                loadedScripts.put(scriptName, script);
                
                // Log script info
                ScriptLoader.ScriptConfig scriptConfig = scriptLoader.getScriptConfig(scriptFile);
                if (scriptConfig != null) {
                    plugin.getLogger().info(String.format(
                        "Loaded script: %s v%s by %s - %s",
                        scriptConfig.getName(),
                        scriptConfig.getVersion(),
                        scriptConfig.getAuthor(),
                        scriptConfig.getDescription()
                    ));
                } else {
                    plugin.getLogger().info("Loaded script: " + scriptName);
                }

                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load script: " + scriptName, e);
        }

        return false;
    }

    /**
     * Reloads all scripts.
     */
    public void reloadScripts() {
        // Get current enabled state of scripts
        Map<String, Boolean> enabledStates = new HashMap<>();
        for (Map.Entry<String, ScriptInstance> entry : loadedScripts.entrySet()) {
            enabledStates.put(entry.getKey(), entry.getValue().isEnabled());
        }

        // Unload all scripts
        for (String scriptName : new ArrayList<>(loadedScripts.keySet())) {
            unloadScript(scriptName);
        }

        // Load all scripts
        loadScripts();

        // Restore enabled states
        for (Map.Entry<String, Boolean> entry : enabledStates.entrySet()) {
            ScriptInstance script = loadedScripts.get(entry.getKey());
            if (script != null && !entry.getValue()) {
                script.disable();
            }
        }
    }

    /**
     * Unloads a script by its name.
     * @param scriptName The name of the script to unload
     */
    public void unloadScript(String scriptName) {
        ScriptInstance script = loadedScripts.get(scriptName);
        if (script != null) {
            script.disable();
            loadedScripts.remove(scriptName);
        }
    }

    /**
     * Starts watching for script changes in the scripts folder.
     */
    private void startWatching() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = scriptsFolder.toPath();
            path.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            watching = true;
            watchThread = new Thread(this::watchDirectory, "ScriptWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            plugin.getLogger().info("Started watching for script changes in " + scriptsFolder.getPath());
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to start script directory watcher", e);
        }
    }

    /**
     * Stops watching for script changes.
     */
    public void stopWatching() {
        watching = false;

        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error closing watch service", e);
            }
            watchService = null;
        }
    }

    /**
     * Watches for changes in the scripts folder.
     */
    private void watchDirectory() {
        try {
            WatchKey key;
            while (watching && (key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    handleFileEvent(event);
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().info("Script directory watcher interrupted");
        } catch (ClosedWatchServiceException e) {
            // Service was closed, exit normally
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error in script directory watcher", e);
        }
    }

    /**
     * Handles a file event in the scripts folder.
     * @param event The file event
     */
    private void handleFileEvent(WatchEvent<?> event) {
        // Ignore non-file events
        if (event.kind() == OVERFLOW) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
        Path filename = pathEvent.context();
        
        // Get the directory being watched
        Path dir = scriptsFolder.toPath();
        File changedFile = dir.resolve(filename).toFile();
        
        // Only process script files and configs
        String name = filename.toString().toLowerCase();
        boolean isScript = name.endsWith(".js") || name.endsWith(".py") || name.endsWith(".rb") || name.endsWith(".wasm");
        boolean isConfig = name.endsWith(".yml") || name.endsWith(".yaml");
        
        if (isScript || isConfig) {
            // Handle the event
            if (event.kind() == ENTRY_DELETE) {
                // File deleted
                if (isScript) {
                    unloadScript(changedFile.getName());
                }
            } else {
                // File created or modified
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (changedFile.exists()) {
                        if (isScript) {
                            loadScript(changedFile);
                        } else if (isConfig) {
                            // If a config changed, reload the associated script
                            String scriptName = name.replaceAll("\\.(yml|yaml)$", "");
                            File scriptFile = new File(changedFile.getParentFile(), scriptName);
                            if (scriptFile.exists()) {
                                File dependency = findDependency(scriptFile, scriptName);
                                if (dependency != null) {
                                    loadScript(dependency);
                                } else {
                                    loadScript(scriptFile);
                                }
                            }
                        }
                    } else if (isScript) {
                        unloadScript(changedFile.getName());
                    }
                });
            }
        }
    }

    private File findDependency(File scriptFile, String depName) {
        // Use the ScriptLoader's package-private method to find dependencies
        return scriptLoader.findDependencyPackage(scriptFile.getParentFile(), depName);
    }

    /**
     * Gets a script instance by its name.
     * @param name The name of the script
     * @return The script instance, or null if not found
     */
    public ScriptInstance getScript(String name) {
        return loadedScripts.get(name);
    }

    /**
     * Gets the script scheduler instance.
     * @return The script scheduler
     */
    public ScriptScheduler getScriptScheduler() {
        return scriptScheduler;
    }

    /**
     * Gets all loaded scripts.
     * @return A collection of all loaded script instances
     */
    public Collection<ScriptInstance> getLoadedScripts() {
        return Collections.unmodifiableCollection(loadedScripts.values());
    }

    /**
     * Shuts down the script manager and all loaded scripts.
     */
    public void shutdown() {
        // Stop watching for file changes
        stopWatching();
        
        // Disable all scripts
        for (ScriptInstance script : loadedScripts.values()) {
            try {
                script.disable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error disabling script: " + script.getScriptFile().getName(), e);
            }
        }
        
        // Clear the loaded scripts
        loadedScripts.clear();
        
        // Shutdown the script scheduler
        if (scriptScheduler != null) {
            scriptScheduler.cancelAllTasks();
        }
    }

}
