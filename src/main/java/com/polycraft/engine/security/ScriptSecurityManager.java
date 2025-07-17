package com.polycraft.engine.security;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.scripting.ScriptInstance;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages script permissions and security policies.
 */
public class ScriptSecurityManager {
    
    private final PolyCraftEngine plugin;
    private final File permissionsFile;
    private final Map<String, Permission> registeredPermissions = new HashMap<>();
    private final Map<String, ScriptSecurityPolicy> scriptPolicies = new HashMap<>();
    
    // Default security policies
    private ScriptSecurityPolicy defaultPolicy;
    
    public ScriptSecurityManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.permissionsFile = new File(plugin.getDataFolder(), "permissions.yml");
        
        // Setup default policy
        this.defaultPolicy = new ScriptSecurityPolicy.Builder()
            .allowNativeAccess(false)
            .allowReflection(false)
            .allowFileSystemAccess(false)
            .allowNetworkAccess(false)
            .allowedPackages("java.lang", "java.util", "org.bukkit")
            .build();
        
        // Load permissions and policies
        loadPermissions();
    }
    
    /**
     * Load permissions from the permissions file.
     */
    public void loadPermissions() {
        if (!permissionsFile.exists()) {
            createDefaultPermissions();
            return;
        }
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(permissionsFile);
            
            // Load script-specific policies
            if (config.isConfigurationSection("policies")) {
                for (String scriptName : config.getConfigurationSection("policies").getKeys(false)) {
                    String path = "policies." + scriptName + ".";
                    
                    ScriptSecurityPolicy.Builder builder = new ScriptSecurityPolicy.Builder();
                    
                    if (config.isBoolean(path + "allowNativeAccess")) {
                        builder.allowNativeAccess(config.getBoolean(path + "allowNativeAccess"));
                    }
                    
                    if (config.isBoolean(path + "allowReflection")) {
                        builder.allowReflection(config.getBoolean(path + "allowReflection"));
                    }
                    
                    if (config.isBoolean(path + "allowFileSystemAccess")) {
                        builder.allowFileSystemAccess(config.getBoolean(path + "allowFileSystemAccess"));
                    }
                    
                    if (config.isBoolean(path + "allowNetworkAccess")) {
                        builder.allowNetworkAccess(config.getBoolean(path + "allowNetworkAccess"));
                    }
                    
                    if (config.isList(path + "allowedPackages")) {
                        List<String> packages = config.getStringList(path + "allowedPackages");
                        builder.allowedPackages(packages.toArray(new String[0]));
                    }
                    
                    scriptPolicies.put(scriptName.toLowerCase(), builder.build());
                }
            }
            
            // Register permissions
            if (config.isConfigurationSection("permissions")) {
                for (String permName : config.getConfigurationSection("permissions").getKeys(false)) {
                    String path = "permissions." + permName + ".";
                    
                    String description = config.getString(path + "description", "");
                    PermissionDefault defaultValue = PermissionDefault.getByName(
                        config.getString(path + "default", "op")
                    );
                    
                    Permission perm = new Permission(permName, description, defaultValue);
                    
                    // Register children
                    if (config.isList(path + "children")) {
                        Map<String, Boolean> children = new HashMap<>();
                        for (String child : config.getStringList(path + "children")) {
                            children.put(child, true);
                        }
                        perm.getChildren().putAll(children);
                    }
                    
                    registerPermission(perm);
                }
            }
            
            plugin.getLogger().info("Loaded " + registeredPermissions.size() + " permissions and " + 
                                 scriptPolicies.size() + " script policies");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading permissions", e);
            createDefaultPermissions();
        }
    }
    
    /**
     * Create default permissions file.
     */
    private void createDefaultPermissions() {
        FileConfiguration config = new YamlConfiguration();
        
        // Default permissions
        config.set("permissions.polycraft.command.use.description", "Allows using the /polycraft command");
        config.set("permissions.polycraft.command.use.default", "true");
        
        config.set("permissions.polycraft.command.reload.description", "Allows reloading scripts");
        config.set("permissions.polycraft.command.reload.default", "op");
        
        config.set("permissions.polycraft.command.eval.description", "Allows evaluating code");
        config.set("permissions.polycraft.command.eval.default", "op");
        
        // Default policies
        config.set("policies.trusted.allowNativeAccess", false);
        config.set("policies.trusted.allowReflection", false);
        config.set("policies.trusted.allowFileSystemAccess", true);
        config.set("policies.trusted.allowNetworkAccess", true);
        config.set("policies.trusted.allowedPackages", Arrays.asList(
            "java.lang", "java.util", "java.io", "java.nio.file",
            "org.bukkit", "org.bukkit.entity"
        ));
        
        try {
            // Create parent directories if they don't exist
            permissionsFile.getParentFile().mkdirs();
            
            // Save the default config
            config.save(permissionsFile);
            plugin.getLogger().info("Created default permissions file");
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create permissions file", e);
        }
    }
    
    /**
     * Register a new permission.
     * @param name The name of the permission
     * @param description The description of the permission
     * @param defaultValue The default value of the permission
     * @return true if the permission was registered, false if it already exists
     */
    public boolean registerPermission(String name, String description, PermissionDefault defaultValue) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        String permName = name.toLowerCase();
        if (!registeredPermissions.containsKey(permName)) {
            Permission perm = new Permission(
                permName, 
                description != null ? description : "", 
                defaultValue != null ? defaultValue : PermissionDefault.OP
            );
            registeredPermissions.put(permName, perm);
            
            try {
                plugin.getServer().getPluginManager().addPermission(perm);
                return true;
            } catch (IllegalArgumentException e) {
                // Permission already registered by another plugin
                plugin.getLogger().log(Level.WARNING, "Permission already registered: " + permName);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Registers a permission with the server.
     * @param permission The permission to register
     * @return true if the permission was registered, false if it already exists
     */
    public boolean registerPermission(Permission permission) {
        if (permission == null) {
            return false;
        }
        
        try {
            plugin.getServer().getPluginManager().addPermission(permission);
            registeredPermissions.put(permission.getName().toLowerCase(), permission);
            return true;
        } catch (IllegalArgumentException e) {
            // Permission already exists
            return false;
        }
    }
    
    /**
     * Unregister a permission.
     * @param name The name of the permission to unregister
     * @return true if the permission was unregistered, false if it didn't exist
     */
    public boolean unregisterPermission(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        String permName = name.toLowerCase();
        Permission perm = registeredPermissions.remove(permName);
        if (perm != null) {
            plugin.getServer().getPluginManager().removePermission(perm);
            return true;
        }
        return false;
    }
    
    /**
     * Unregisters a permission from the server.
     * @param permission The permission to unregister
     * @return true if the permission was unregistered, false if it didn't exist
     */
    public boolean unregisterPermission(Permission permission) {
        if (permission == null) {
            return false;
        }
        
        return unregisterPermission(permission.getName());
    }
    
    /**
     * Unregister all permissions registered by this security manager.
     * @return The number of permissions that were unregistered
     */
    public int unregisterAllPermissions() {
        int count = 0;
        for (Permission perm : new ArrayList<>(registeredPermissions.values())) {
            if (unregisterPermission(perm)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Check if a player has a permission.
     */
    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission("polycraft." + permission) || 
               player.hasPermission("polycraft.*") ||
               player.isOp();
    }
    
    /**
     * Check if a script has a specific permission.
     * @param script The script instance
     * @param permission The permission to check
     * @return true if the script has the permission, false otherwise
     */
    public boolean hasPermission(ScriptInstance script, String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        
        // Check if the permission is registered
        Permission perm = registeredPermissions.get(permission.toLowerCase());
        if (perm == null) {
            // Permission not explicitly defined, use default
            return Permission.DEFAULT_PERMISSION.getValue(false);
        }
        
        // Check if the script has the permission
        return script.hasPermission(permission);
    }
    
    /**
     * Gets the security policy for a script.
     * @param scriptName The name of the script
     * @return The security policy, or default policy if not found
     */
    public ScriptSecurityPolicy getPolicy(String scriptName) {
        if (scriptName == null) {
            return defaultPolicy;
        }
        
        // Check for exact match first
        ScriptSecurityPolicy policy = scriptPolicies.get(scriptName.toLowerCase());
        if (policy != null) {
            return policy;
        }
        
        // Check for wildcard policies
        for (Map.Entry<String, ScriptSecurityPolicy> entry : scriptPolicies.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("*") && scriptName.matches(key.replace("*", ".*"))) {
                return entry.getValue();
            }
        }
        
        // Return default policy if no match found
        return defaultPolicy;
    }
    
    /**
     * Gets the security policy for a script instance.
     * @param script The script instance
     * @return The security policy for the script, or default policy if not found
     */
    public ScriptSecurityPolicy getPolicy(ScriptInstance script) {
        if (script == null || script.getScriptFile() == null) {
            return defaultPolicy;
        }
        return getPolicy(script.getScriptFile().getName());
    }
    
    /**
     * Get the default security policy.
     * @return The default security policy
     */
    public ScriptSecurityPolicy getDefaultPolicy() {
        return defaultPolicy;
    }
    
    /**
     * Checks if a class is allowed to be accessed by scripts.
     * @param className The fully qualified name of the class to check
     * @return true if the class is allowed, false otherwise
     */
    public boolean isClassAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        
        // Check against the default policy's allowed packages
        for (String pkg : defaultPolicy.getAllowedPackages()) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Set the default security policy.
     */
    public void setDefaultPolicy(ScriptSecurityPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy != null ? defaultPolicy : new ScriptSecurityPolicy.Builder().build();
    }
}
