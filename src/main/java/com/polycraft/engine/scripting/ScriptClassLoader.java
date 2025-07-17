package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.security.ScriptSecurityPolicy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * A custom class loader for script isolation and security.
 */
public class ScriptClassLoader extends URLClassLoader {
    
    private final PolyCraftEngine plugin;
    private final ScriptSecurityPolicy securityPolicy;
    private final Map<String, Class<?>> definedClasses = new HashMap<>();
    private final ProtectionDomain protectionDomain;
    
    public ScriptClassLoader(PolyCraftEngine plugin, ScriptSecurityPolicy securityPolicy) {
        super(new URL[0], plugin.getClass().getClassLoader());
        this.plugin = plugin;
        this.securityPolicy = securityPolicy != null ? securityPolicy : new ScriptSecurityPolicy.Builder().build();
        
        // Create a protection domain for loaded classes
        CodeSource codeSource = new CodeSource(null, (Certificate[]) null);
        this.protectionDomain = new ProtectionDomain(codeSource, createPermissions());
    }
    
    /**
     * Create permissions based on the security policy.
     */
    private PermissionCollection createPermissions() {
        Permissions permissions = new Permissions();
        
        // Add permissions based on the security policy
        if (securityPolicy.isFileSystemAccessAllowed()) {
            // Add file permissions if needed
        }
        
        if (securityPolicy.isNetworkAccessAllowed()) {
            // Add network permissions if needed
        }
        
        return permissions;
    }
    
    /**
     * Add a URL to the classpath.
     */
    public void addURL(URL url) {
        super.addURL(url);
    }
    
    /**
     * Define a new class from bytecode.
     */
    public Class<?> defineClass(String name, byte[] bytecode) {
        synchronized (getClassLoadingLock(name)) {
            // Check if the class is already defined
            Class<?> existing = findLoadedClass(name);
            if (existing != null) {
                return existing;
            }
            
            // Define the class with our protection domain
            Class<?> clazz = defineClass(name, bytecode, 0, bytecode.length, protectionDomain);
            definedClasses.put(name, clazz);
            return clazz;
        }
    }
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if the class is already loaded
        Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
            return clazz;
        }
        
        // Check if the class is in our defined classes
        clazz = definedClasses.get(name);
        if (clazz != null) {
            return clazz;
        }
        
        // Check if the class is allowed by the security policy
        if (!securityPolicy.isClassAllowed(name)) {
            throw new SecurityException("Access denied to class: " + name);
        }
        
        try {
            // Try to load the class from the parent (plugin) classloader first
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            // If not found in parent, try to load it from our URLs
            String path = name.replace('.', '/').concat(".class");
            
            try (InputStream in = getResourceAsStream(path)) {
                if (in != null) {
                    // Read the class bytes
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, bytesRead);
                    }
                    buffer.flush();
                    
                    // Define the class
                    byte[] bytecode = buffer.toByteArray();
                    return defineClass(name, bytecode);
                }
            } catch (IOException e2) {
                plugin.getLogger().log(Level.WARNING, "Error loading class " + name, e2);
            }
            
            throw new ClassNotFoundException(name);
        }
    }
    
    @Override
    public void close() {
        try {
            super.close();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing class loader", e);
        } finally {
            definedClasses.clear();
        }
    }
    
    /**
     * Get all classes defined by this class loader.
     */
    public Map<String, Class<?>> getDefinedClasses() {
        return new HashMap<>(definedClasses);
    }
    
    /**
     * Get the security policy for this class loader.
     */
    public ScriptSecurityPolicy getSecurityPolicy() {
        return securityPolicy;
    }
}
