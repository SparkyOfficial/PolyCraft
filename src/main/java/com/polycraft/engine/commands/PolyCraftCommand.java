package com.polycraft.engine.commands;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PolyCraftCommand implements CommandExecutor, TabCompleter {
    
    private final PolyCraftEngine plugin;
    private final Map<String, CommandHandler> subCommands = new HashMap<>();
    
    public PolyCraftCommand(PolyCraftEngine plugin) {
        this.plugin = plugin;
        
        // Register sub-commands
        registerSubCommand("list", this::handleList);
        registerSubCommand("reload", this::handleReload);
        registerSubCommand("reload-all", this::handleReloadAll);
        registerSubCommand("enable", this::handleEnable);
        registerSubCommand("disable", this::handleDisable);
        registerSubCommand("status", this::handleStatus);
        registerSubCommand("eval", this::handleEval);
        registerSubCommand("help", this::handleHelp);
    }
    
    private void registerSubCommand(String name, CommandHandler handler) {
        subCommands.put(name.toLowerCase(), handler);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleHelp(sender, new String[0]);
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        CommandHandler handler = subCommands.get(subCommand);
        if (handler != null) {
            if (!sender.hasPermission("polycraft.command." + subCommand) && !sender.hasPermission("polycraft.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            return handler.handle(sender, subArgs);
        }
        
        sender.sendMessage(ChatColor.RED + "Unknown command. Type '/pc help' for help.");
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Tab complete sub-commands
            return filterCompletions(args[0], subCommands.keySet());
        }
        
        String subCommand = args[0].toLowerCase();
        
        // Tab complete script names for relevant commands
        if (args.length == 2 && (subCommand.equals("reload") || 
                                subCommand.equals("enable") || 
                                subCommand.equals("disable") || 
                                subCommand.equals("status"))) {
            List<String> scriptNames = plugin.getScriptManager().getLoadedScripts().stream()
                    .map(script -> script.getScriptFile().getName())
                    .collect(Collectors.toList());
            return filterCompletions(args[1], scriptNames);
        }
        
        // Tab complete languages for eval command
        if (subCommand.equals("eval") && args.length == 2) {
            return filterCompletions(args[1], 
                    Arrays.asList("js", "javascript", "python", "py", "java", "csharp", "cs"));
        }
        
        return Collections.emptyList();
    }
    
    private List<String> filterCompletions(String input, Collection<String> options) {
        return options.stream()
                .filter(opt -> opt.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    // Command Handlers
    
    private boolean handleList(CommandSender sender, String[] args) {
        Collection<ScriptInstance> scripts = plugin.getScriptManager().getLoadedScripts();
        
        if (scripts.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No scripts are currently loaded.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Loaded Scripts (" + scripts.size() + ") ===");
        for (ScriptInstance script : scripts) {
            ChatColor statusColor = script.isEnabled() ? ChatColor.GREEN : ChatColor.RED;
            String status = script.isEnabled() ? "Enabled" : "Disabled";
            sender.sendMessage(String.format("%s- %s %s(%s, %s)",
                    ChatColor.WHITE,
                    script.getScriptFile().getName(),
                    statusColor,
                    status,
                    script.getLanguage().name().toLowerCase()
            ));
        }
        return true;
    }
    
    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /pc reload <script>");
            return true;
        }
        
        String scriptName = args[0];
        ScriptInstance script = plugin.getScriptManager().getScript(scriptName);
        
        if (script == null) {
            sender.sendMessage(ChatColor.RED + "Script not found: " + scriptName);
            return true;
        }
        
        boolean success = plugin.getScriptManager().reloadScript(scriptName);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Reloaded script: " + scriptName);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to reload script: " + scriptName);
        }
        
        return true;
    }
    
    private boolean handleReloadAll(CommandSender sender, String[] args) {
        plugin.getScriptManager().reloadAllScripts();
        sender.sendMessage(ChatColor.GREEN + "Reloaded all scripts.");
        return true;
    }
    
    private boolean handleEnable(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /pc enable <script>");
            return true;
        }
        
        // Implementation for enabling scripts
        sender.sendMessage(ChatColor.YELLOW + "This feature is not yet implemented.");
        return true;
    }
    
    private boolean handleDisable(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /pc disable <script>");
            return true;
        }
        
        // Implementation for disabling scripts
        sender.sendMessage(ChatColor.YELLOW + "This feature is not yet implemented.");
        return true;
    }
    
    private boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /pc status <script>");
            return true;
        }
        
        String scriptName = args[0];
        ScriptInstance script = plugin.getScriptManager().getScript(scriptName);
        
        if (script == null) {
            sender.sendMessage(ChatColor.RED + "Script not found: " + scriptName);
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Script Status ===");
        sender.sendMessage(ChatColor.WHITE + "Name: " + ChatColor.GRAY + script.getScriptFile().getName());
        sender.sendMessage(ChatColor.WHITE + "Status: " + 
                (script.isEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.WHITE + "Language: " + ChatColor.GRAY + script.getLanguage().name());
        sender.sendMessage(ChatColor.WHITE + "Last Modified: " + ChatColor.GRAY + 
                new Date(script.getLastModified()).toString());
        
        return true;
    }
    
    private boolean handleEval(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /pc eval <language> <code>");
            sender.sendMessage(ChatColor.GRAY + "Example: /pc eval js poly.log('Hello, World!');");
            return true;
        }
        
        // For security, only allow console and OPs to use eval
        if (sender instanceof org.bukkit.entity.Player && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        String language = args[0].toLowerCase();
        String code = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // Map language aliases
        switch (language) {
            case "js":
            case "javascript":
                language = "js";
                break;
            case "py":
            case "python":
                language = "python";
                break;
            case "cs":
            case "csharp":
                language = "csharp";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unsupported language: " + language);
                return true;
        }
        
        try {
            // Call the new evaluateCode method with the sender
            Object result = plugin.getScriptManager().evaluateCode(language, code, sender);
            
            // Convert the result to a string for display
            String resultString = "null";
            if (result != null) {
                if (result instanceof org.graalvm.polyglot.Value) {
                    org.graalvm.polyglot.Value val = (org.graalvm.polyglot.Value) result;
                    if (val.isHostObject()) {
                        resultString = String.valueOf(val.asHostObject());
                    } else if (!val.isNull()) {
                        resultString = val.toString();
                    }
                } else {
                    resultString = result.toString();
                }
            }
            
            sender.sendMessage(ChatColor.GREEN + "Result: " + ChatColor.WHITE + resultString);
        } catch (Exception e) {
            // Provide a more detailed error message
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }
            sender.sendMessage(ChatColor.RED + "Error: " + errorMsg);
            
            // Log the full exception to console for debugging
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error evaluating code", e);
        }
        
        return true;
    }
    
    private boolean handleHelp(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== PolyCraft Engine Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/pc list" + ChatColor.WHITE + " - List all loaded scripts");
        sender.sendMessage(ChatColor.YELLOW + "/pc reload <script>" + ChatColor.WHITE + " - Reload a script");
        sender.sendMessage(ChatColor.YELLOW + "/pc reload-all" + ChatColor.WHITE + " - Reload all scripts");
        sender.sendMessage(ChatColor.YELLOW + "/pc enable <script>" + ChatColor.WHITE + " - Enable a script");
        sender.sendMessage(ChatColor.YELLOW + "/pc disable <script>" + ChatColor.WHITE + " - Disable a script");
        sender.sendMessage(ChatColor.YELLOW + "/pc status <script>" + ChatColor.WHITE + " - Show script status");
        sender.sendMessage(ChatColor.YELLOW + "/pc eval <lang> <code>" + ChatColor.WHITE + " - Execute code");
        sender.sendMessage(ChatColor.YELLOW + "/pc help" + ChatColor.WHITE + " - Show this help");
        return true;
    }
    
    @FunctionalInterface
    private interface CommandHandler {
        boolean handle(CommandSender sender, String[] args);
    }
}
