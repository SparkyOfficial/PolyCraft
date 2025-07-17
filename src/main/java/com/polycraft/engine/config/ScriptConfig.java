package com.polycraft.engine.config;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages configuration for individual scripts.
 */
public class ScriptConfig {
    
    private final PolyCraftEngine plugin;
    private final File configFile;
    private FileConfiguration config;
    private final Map<String, Object> defaults;
    
    public ScriptConfig(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.defaults = new HashMap<>();
        
        // Set default values
        defaults.put("debug", false);
        defaults.put("auto-update", true);
        defaults.put("language", "javascript");
        
        loadConfig();
    }
    
    /**
     * Loads the configuration from the specified file.
     * @param file The file to load the configuration from
     * @return true if the configuration was loaded successfully, false otherwise
     */
    public boolean load(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        try {
            this.config = YamlConfiguration.loadConfiguration(file);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load configuration from " + file.getName(), e);
            return false;
        }
    }
    
    /**
     * Load the configuration from disk.
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("Created default config file");
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Set defaults if they don't exist
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
            }
        }
        
        saveConfig();
    }
    
    /**
     * Save the configuration to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }
    
    /**
     * Reload the configuration from disk.
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    // Getters for configuration values
    
    public boolean isDebugEnabled() {
        return config.getBoolean("debug");
    }
    
    public boolean isAutoUpdateEnabled() {
        return config.getBoolean("auto-update");
    }
    
    public String getDefaultLanguage() {
        return config.getString("language");
    }
    
    /**
     * Get a configuration value by key.
     * @param key The configuration key
     * @return The value, or null if not found
     */
    public Object get(String key) {
        return config.get(key);
    }
    
    /**
     * Get a configuration value by key with a default value.
     * @param key The configuration key
     * @param def The default value to return if the key doesn't exist
     * @return The value, or the default if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T def) {
        Object value = config.get(key);
        return value != null ? (T) value : def;
    }
    
    /**
     * Set a configuration value.
     * @param key The configuration key
     * @param value The value to set
     */
    public void set(String key, Object value) {
        config.set(key, value);
        saveConfig();
    }
}
