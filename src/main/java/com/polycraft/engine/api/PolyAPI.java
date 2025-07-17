package com.polycraft.engine.api;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main API class that provides access to Minecraft functionality from scripts.
 * This class is exposed to scripts as the 'poly' global object.
 */
public class PolyAPI {
    
    private final PolyCraftEngine plugin;
    private final CommandSender console;
    
    public PolyAPI(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.console = Bukkit.getConsoleSender();
    }
    
    // Logging utilities
    public void log(String message) {
        console.sendMessage("[" + plugin.getName() + "] " + message);
    }
    
    public void warn(String message) {
        console.sendMessage("[" + plugin.getName() + "] [WARN] " + message);
    }
    
    public void error(String message) {
        console.sendMessage("[" + plugin.getName() + "] [ERROR] " + message);
    }
    
    // Server utilities
    public void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }
    
    public void dispatchCommand(String command) {
        Bukkit.dispatchCommand(console, command);
    }
    
    // Player utilities
    public Player getPlayer(String name) {
        return Bukkit.getPlayer(name);
    }
    
    public Player getPlayer(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }
    
    public List<Player> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().collect(Collectors.toList());
    }
    
    // World utilities
    public World getWorld(String name) {
        return Bukkit.getWorld(name);
    }
    
    public Block getBlockAt(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }
    
    public Block getBlockAt(Location location) {
        return location.getBlock();
    }
    
    // Item utilities
    public ItemStack createItem(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null ? new ItemStack(material) : null;
    }
    
    public ItemStack createItem(String materialName, int amount) {
        Material material = Material.matchMaterial(materialName);
        return material != null ? new ItemStack(material, amount) : null;
    }
    
    // Scheduler utilities
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
    
    public void runTaskLater(Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
    
    public void runTaskTimer(Runnable task, long delay, long period) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }
    
    public void runTaskAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    // Configuration utilities
    public void saveConfig() {
        plugin.saveConfig();
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
    }
    
    // Plugin management
    public void disablePlugin() {
        Bukkit.getPluginManager().disablePlugin(plugin);
    }
    
    // Utility methods
    public String formatLocation(Location location) {
        return String.format("%s: %d, %d, %d", 
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }
}
