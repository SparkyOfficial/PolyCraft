package com.polycraft.engine.commands;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Manages command registration and execution for scripts.
 */
public class ScriptCommandManager implements CommandExecutor, TabCompleter {
    
    private final PolyCraftEngine plugin;
    private final Map<String, ScriptCommand> commands;
    private final Map<ScriptInstance, Set<String>> scriptCommands;
    
    public ScriptCommandManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.commands = new ConcurrentHashMap<>();
        this.scriptCommands = new ConcurrentHashMap<>();
    }
    
    /**
     * Register a command for a script.
     * @param script The script instance
     * @param name The command name
     * @param aliases Command aliases
     * @param description Command description
     * @param usage Command usage
     * @param permission Command permission
     * @param permissionMessage Permission denied message
     * @param handler Command handler
     * @return true if registration was successful
     */
    public boolean registerCommand(
            ScriptInstance script,
            String name,
            List<String> aliases,
            String description,
            String usage,
            String permission,
            String permissionMessage,
            BiConsumer<CommandSender, String[]> handler) {
        
        // Normalize command name
        String cmdName = name.toLowerCase();
        
        // Check if command is already registered
        if (commands.containsKey(cmdName)) {
            return false;
        }
        
        // Create and register the command
        PluginCommand command = plugin.getCommand(cmdName);
        if (command == null) {
            // Create command using reflection since PluginCommand constructor is protected
            try {
                command = (PluginCommand) PluginCommand.class
                    .getDeclaredConstructor(String.class, Plugin.class)
                    .newInstance(cmdName, plugin);
                
                command.setAliases(aliases != null ? aliases : Collections.emptyList());
                command.setDescription(description != null ? description : "");
                command.setUsage(usage != null ? usage : "/" + cmdName);
                command.setPermission(permission);
                command.setPermissionMessage(permissionMessage);
                
                // Register the command
                // Get the command map
                org.bukkit.command.SimpleCommandMap commandMap = (org.bukkit.command.SimpleCommandMap) 
                    Bukkit.getServer().getClass()
                        .getMethod("getCommandMap")
                        .invoke(Bukkit.getServer());
                
                // Register the command
                commandMap.register(plugin.getName().toLowerCase(), command);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to register command: " + cmdName, e);
                return false;
            }
        }
        
        // Set the executor and tab completer
        command.setExecutor(this);
        command.setTabCompleter(this);
        
        // Create and store the script command
        ScriptCommand scriptCommand = new ScriptCommand(
            script,
            cmdName,
            handler,
            command
        );
        
        commands.put(cmdName, scriptCommand);
        scriptCommands.computeIfAbsent(script, k -> ConcurrentHashMap.newKeySet()).add(cmdName);
        
        return true;
    }
    
    /**
     * Unregister a command.
     * @param name The command name
     * @return true if the command was unregistered
     */
    public boolean unregisterCommand(String name) {
        String cmdName = name.toLowerCase();
        ScriptCommand command = commands.remove(cmdName);
        
        if (command != null) {
            // Remove from script commands
            Set<String> scriptCmds = scriptCommands.get(command.getScript());
            if (scriptCmds != null) {
                scriptCmds.remove(cmdName);
                if (scriptCmds.isEmpty()) {
                    scriptCommands.remove(command.getScript());
                }
            }
            
            // Unregister from Bukkit
            try {
                // Get the command map
                org.bukkit.command.SimpleCommandMap commandMap = (org.bukkit.command.SimpleCommandMap) 
                    Bukkit.getServer().getClass()
                        .getMethod("getCommandMap")
                        .invoke(Bukkit.getServer());
                
                // Unregister the command
                org.bukkit.command.Command bukkitCommand = commandMap.getCommand(cmdName);
                if (bukkitCommand != null) {
                    // Get known commands using getKnownCommands() method
                    Map<String, org.bukkit.command.Command> knownCommands = getKnownCommands(commandMap);
                    
                    // Remove the main command
                    knownCommands.remove(cmdName);
                    
                    // Remove aliases
                    for (String alias : command.getCommand().getAliases()) {
                        knownCommands.remove(alias.toLowerCase());
                    }
                    
                    // Unregister the command
                    bukkitCommand.unregister(commandMap);
                }
                
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unregister command: " + cmdName, e);
            }
        }
        
        return false;
    }
    
    /**
     * Unregister all commands for a script.
     * @param script The script instance
     */
    public void unregisterCommands(ScriptInstance script) {
        Set<String> commands = scriptCommands.remove(script);
        if (commands != null) {
            for (String cmdName : new ArrayList<>(commands)) {
                unregisterCommand(cmdName);
            }
        }
    }
    
    /**
     * Unregister all commands.
     */
    public void unregisterAll() {
        for (String cmdName : new ArrayList<>(commands.keySet())) {
            unregisterCommand(cmdName);
        }
        commands.clear();
        scriptCommands.clear();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ScriptCommand scriptCommand = commands.get(command.getName().toLowerCase());
        
        if (scriptCommand != null) {
            try {
                // Check permission
                if (command.getPermission() != null && !command.getPermission().isEmpty() && 
                    !sender.hasPermission(command.getPermission())) {
                    
                    String message = command.getPermissionMessage();
                    if (message != null && !message.isEmpty()) {
                        sender.sendMessage(message);
                    } else {
                        sender.sendMessage("You don't have permission to use this command.");
                    }
                    return true;
                }
                
                // Execute the command
                scriptCommand.getHandler().accept(sender, args);
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error executing command: " + command.getName(), e);
                sender.sendMessage("An error occurred while executing this command.");
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // TODO: Implement tab completion for script commands
        return Collections.emptyList();
    }
    
    /**
     * Get all registered commands.
     */
    public Map<String, ScriptCommand> getCommands() {
        return new HashMap<>(commands);
    }
    
    /**
     * Get all commands registered by a script.
     */
    public Set<String> getCommands(ScriptInstance script) {
        Set<String> cmds = scriptCommands.get(script);
        return cmds != null ? new HashSet<>(cmds) : Collections.emptySet();
    }
    
    /**
     * Gets the known commands from a command map.
     * @param commandMap The command map
     * @return A map of command names to command instances
     */
    @SuppressWarnings("unchecked")
    private Map<String, org.bukkit.command.Command> getKnownCommands(org.bukkit.command.CommandMap commandMap) {
        try {
            // Try to get the knownCommands field using reflection
            java.lang.reflect.Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            return (Map<String, org.bukkit.command.Command>) knownCommandsField.get(commandMap);
        } catch (Exception e) {
            // Fallback to an empty map if we can't access the known commands
            plugin.getLogger().warning("Failed to get known commands: " + e.getMessage());
            return new java.util.HashMap<>();
        }
    }
    
    /**
     * Clean up resources.
     */
    public void shutdown() {
        unregisterAll();
    }
    
    /**
     * Represents a script-registered command.
     */
    public static class ScriptCommand {
        private final ScriptInstance script;
        private final String name;
        private final BiConsumer<CommandSender, String[]> handler;
        private final PluginCommand command;
        
        public ScriptCommand(
                ScriptInstance script,
                String name,
                BiConsumer<CommandSender, String[]> handler,
                PluginCommand command) {
            this.script = script;
            this.name = name;
            this.handler = handler;
            this.command = command;
        }
        
        public ScriptInstance getScript() {
            return script;
        }
        
        public String getName() {
            return name;
        }
        
        public BiConsumer<CommandSender, String[]> getHandler() {
            return handler;
        }
        
        public PluginCommand getCommand() {
            return command;
        }
    }
}
