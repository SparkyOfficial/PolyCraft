package com.polycraft.engine.config;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages configuration files for scripts.
 */
public class ScriptConfigManager {
    
    private final PolyCraftEngine plugin;
    private final File configDir;
    private final Map<String, ScriptConfig> configs;
    private final Map<ScriptInstance, Set<String>> scriptConfigs;
    
    public ScriptConfigManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.configDir = new File(plugin.getDataFolder(), "script-configs");
        this.configs = new ConcurrentHashMap<>();
        this.scriptConfigs = new ConcurrentHashMap<>();
        
        // Create config directory if it doesn't exist
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
    }
    
    /**
     * Load or create a configuration file for a script.
     * @param script The script instance
     * @param name The configuration name
     * @param defaultConfig Default configuration values (can be null)
     * @return The loaded or created configuration
     */
    public ScriptConfig getConfig(ScriptInstance script, String name, Map<String, Object> defaultConfig) {
        String configName = generateConfigName(script, name);
        
        // Return existing config if available
        ScriptConfig config = configs.get(configName);
        if (config != null) {
            return config;
        }
        
        // Create new config
        File configFile = new File(configDir, configName + ".yml");
        config = new ScriptConfig(script, configFile, defaultConfig);
        
        // Load existing config if it exists
        if (configFile.exists()) {
            try {
                config.load();
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load config: " + configName, e);
                // Continue with default values
            }
        } else {
            // Save default config
            try {
                config.save();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save default config: " + configName, e);
            }
        }
        
        // Store the config
        configs.put(configName, config);
        scriptConfigs.computeIfAbsent(script, k -> ConcurrentHashMap.newKeySet()).add(configName);
        
        return config;
    }
    
    /**
     * Save all configurations.
     */
    public void saveAll() {
        for (ScriptConfig config : configs.values()) {
            try {
                config.save();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + config.getName(), e);
            }
        }
    }
    
    /**
     * Save configurations for a specific script.
     * @param script The script instance
     */
    public void saveConfigs(ScriptInstance script) {
        Set<String> configNames = scriptConfigs.get(script);
        if (configNames != null) {
            for (String configName : configNames) {
                ScriptConfig config = configs.get(configName);
                if (config != null) {
                    try {
                        config.save();
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + configName, e);
                    }
                }
            }
        }
    }
    
    /**
     * Reload all configurations from disk.
     */
    public void reloadAll() {
        for (ScriptConfig config : configs.values()) {
            try {
                config.reload();
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload config: " + config.getName(), e);
            }
        }
    }
    
    /**
     * Remove all configurations for a script.
     * @param script The script instance
     */
    public void removeConfigs(ScriptInstance script) {
        Set<String> configNames = scriptConfigs.remove(script);
        if (configNames != null) {
            for (String configName : configNames) {
                configs.remove(configName);
            }
        }
    }
    
    /**
     * Backup all configurations.
     * @param backupDir The backup directory
     * @throws IOException If an I/O error occurs
     */
    public void backupConfigs(File backupDir) throws IOException {
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        
        // Create a timestamped backup directory
        String timestamp = String.format("%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS", System.currentTimeMillis());
        File backupSubDir = new File(backupDir, "script-configs-" + timestamp);
        backupSubDir.mkdirs();
        
        // Copy all config files
        for (File file : configDir.listFiles((dir, name) -> name.endsWith(".yml"))) {
            Path source = file.toPath();
            Path target = Paths.get(backupSubDir.getPath(), file.getName());
            Files.copy(source, target);
        }
    }
    
    /**
     * Get all configurations for a script.
     * @param script The script instance
     * @return A map of configuration names to configurations
     */
    public Map<String, ScriptConfig> getConfigs(ScriptInstance script) {
        Map<String, ScriptConfig> result = new HashMap<>();
        Set<String> configNames = scriptConfigs.get(script);
        
        if (configNames != null) {
            for (String configName : configNames) {
                ScriptConfig config = configs.get(configName);
                if (config != null) {
                    String name = extractConfigName(script, configName);
                    result.put(name, config);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Clean up resources.
     */
    public void shutdown() {
        saveAll();
        configs.clear();
        scriptConfigs.clear();
    }
    
    /**
     * Generate a unique configuration name for a script.
     */
    private String generateConfigName(ScriptInstance script, String name) {
        String scriptName = script.getScriptFile().getName();
        scriptName = scriptName.substring(0, scriptName.lastIndexOf('.'));
        return scriptName + "_" + name.toLowerCase().replace(" ", "_");
    }
    
    /**
     * Extract the original configuration name from a generated name.
     */
    private String extractConfigName(ScriptInstance script, String configName) {
        String scriptName = script.getScriptFile().getName();
        scriptName = scriptName.substring(0, scriptName.lastIndexOf('.'));
        return configName.substring(scriptName.length() + 1);
    }
    
    /**
     * Represents a script configuration file.
     */
    public static class ScriptConfig {
        private final ScriptInstance script;
        private final File configFile;
        private final Map<String, Object> defaults;
        private FileConfiguration config;
        
        public ScriptConfig(ScriptInstance script, File configFile, Map<String, Object> defaults) {
            this.script = script;
            this.configFile = configFile;
            this.defaults = defaults != null ? new HashMap<>(defaults) : new HashMap<>();
            this.config = new YamlConfiguration();
        }
        
        /**
         * Load the configuration from disk.
         */
        public void load() throws IOException, InvalidConfigurationException {
            config.load(configFile);
            
            // Set defaults if they don't exist
            boolean changed = false;
            for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                if (!config.contains(entry.getKey())) {
                    config.set(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            
            // Save if defaults were added
            if (changed) {
                save();
            }
        }
        
        /**
         * Save the configuration to disk.
         */
        public void save() throws IOException {
            config.save(configFile);
        }
        
        /**
         * Reload the configuration from disk.
         */
        public void reload() throws IOException, InvalidConfigurationException {
            config = new YamlConfiguration();
            load();
        }
        
        /**
         * Get a configuration value.
         */
        public <T> T get(String path, Class<T> type) {
            Object value = config.get(path);
            return type.isInstance(value) ? type.cast(value) : null;
        }
        
        /**
         * Get a configuration value with a default.
         */
        @SuppressWarnings("unchecked")
        public <T> T get(String path, T def) {
            Object value = config.get(path, def);
            return (T) value;
        }
        
        /**
         * Set a configuration value.
         */
        public void set(String path, Object value) {
            config.set(path, value);
        }
        
        /**
         * Check if the configuration contains a key.
         */
        public boolean contains(String path) {
            return config.contains(path);
        }
        
        /**
         * Get all keys in the configuration.
         */
        public Set<String> getKeys() {
            return config.getKeys(false);
        }
        
        /**
         * Get the configuration name.
         */
        public String getName() {
            return configFile.getName();
        }
        
        /**
         * Get the underlying FileConfiguration.
         */
        public FileConfiguration getHandle() {
            return config;
        }
    }
}
