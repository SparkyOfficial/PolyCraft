package com.polycraft.engine.data;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages persistent data storage for scripts.
 */
public class ScriptDataManager {
    
    private final PolyCraftEngine plugin;
    private final File dataFolder;
    private final Map<String, FileConfiguration> dataCache = new HashMap<>();
    
    public ScriptDataManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        
        // Create data directory if it doesn't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    /**
     * Loads data from the specified file.
     * @param file The file to load data from
     * @return true if the data was loaded successfully, false otherwise
     */
    public boolean load(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        try {
            String fileName = file.getName();
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            dataCache.put(fileName, config);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load data from " + file.getName(), e);
            return false;
        }
    }
    
    /**
     * Get or create a data file for a script.
     * @param script The script instance
     * @return The configuration file for the script
     */
    public FileConfiguration getScriptData(ScriptInstance script) {
        String fileName = script.getScriptFile().getName() + ".yml";
        return getDataFile(fileName);
    }
    
    /**
     * Get or create a data file for a player.
     * @param playerId The player's UUID
     * @return The configuration file for the player
     */
    public FileConfiguration getPlayerData(UUID playerId) {
        return getDataFile("players/" + playerId + ".yml");
    }
    
    /**
     * Save all modified data files.
     */
    public void saveAll() {
        for (Map.Entry<String, FileConfiguration> entry : dataCache.entrySet()) {
            saveDataFile(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Save a specific data file.
     * @param script The script to save data for
     */
    public void saveScriptData(ScriptInstance script) {
        String fileName = script.getScriptFile().getName() + ".yml";
        FileConfiguration config = dataCache.get(fileName);
        if (config != null) {
            saveDataFile(fileName, config);
        }
    }
    
    /**
     * Clear the data cache.
     */
    public void clearCache() {
        saveAll();
        dataCache.clear();
    }
    
    private FileConfiguration getDataFile(String fileName) {
        // Return from cache if available
        if (dataCache.containsKey(fileName)) {
            return dataCache.get(fileName);
        }
        
        // Create parent directories if they don't exist
        File dataFile = new File(dataFolder, fileName);
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }
        
        // Load or create the configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        dataCache.put(fileName, config);
        
        return config;
    }
    
    private void saveDataFile(String fileName, FileConfiguration config) {
        try {
            File dataFile = new File(dataFolder, fileName);
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data to " + fileName, e);
        }
    }
}
