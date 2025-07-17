package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages script dependencies and resolution.
 */
public class DependencyManager {
    
    private final PolyCraftEngine plugin;
    private final File libsDir;
    private final Map<String, ScriptMetadata> dependencyCache;
    
    public DependencyManager(PolyCraftEngine plugin) {
        this.plugin = plugin;
        this.libsDir = new File(plugin.getDataFolder(), "libs");
        this.dependencyCache = new HashMap<>();
        
        // Create libs directory if it doesn't exist
        if (!libsDir.exists()) {
            libsDir.mkdirs();
        }
    }
    
    /**
     * Resolve all dependencies for a script.
     * @param script The script to resolve dependencies for
     * @return A list of resolved dependencies in load order
     * @throws DependencyResolutionException If dependencies cannot be resolved
     */
    public List<ScriptMetadata> resolveDependencies(ScriptMetadata script) throws DependencyResolutionException {
        return resolveDependencies(script, new HashSet<>(), new HashSet<>());
    }
    
    private List<ScriptMetadata> resolveDependencies(
            ScriptMetadata script,
            Set<String> resolved,
            Set<String> seen) throws DependencyResolutionException {
        
        String scriptId = script.getFullId();
        
        // Check for circular dependencies
        if (seen.contains(scriptId)) {
            throw new DependencyResolutionException("Circular dependency detected: " + 
                String.join(" -> ", seen) + " -> " + scriptId);
        }
        
        // If already resolved, return empty list (no need to process again)
        if (resolved.contains(scriptId)) {
            return new ArrayList<>();
        }
        
        seen.add(scriptId);
        
        List<ScriptMetadata> dependencies = new ArrayList<>();
        
        // Process hard dependencies first
        for (String dep : script.getDepends()) {
            ScriptMetadata dependency = resolveDependency(dep, script);
            dependencies.addAll(resolveDependencies(dependency, resolved, new HashSet<>(seen)));
        }
        
        // Then process soft dependencies
        for (String dep : script.getSoftDepends()) {
            try {
                ScriptMetadata dependency = resolveDependency(dep, script);
                if (dependency != null) {
                    dependencies.addAll(resolveDependencies(dependency, resolved, new HashSet<>(seen)));
                }
            } catch (DependencyResolutionException e) {
                plugin.getLogger().warning("Soft dependency not found: " + dep + " for " + script.getId());
            }
        }
        
        // Add self to resolved
        if (!resolved.contains(scriptId)) {
            dependencies.add(script);
            resolved.add(scriptId);
        }
        
        return dependencies;
    }
    
    /**
     * Resolve a single dependency.
     */
    private ScriptMetadata resolveDependency(String dep, ScriptMetadata requester) 
            throws DependencyResolutionException {
        
        // Parse dependency string (format: name[@version])
        String[] parts = dep.split("@", 2);
        String name = parts[0].trim();
        String version = parts.length > 1 ? parts[1].trim() : "*";
        
        // Check cache first
        ScriptMetadata cached = dependencyCache.get(name.toLowerCase());
        if (cached != null) {
            if (version.equals("*") || isVersionCompatible(cached.getVersion(), version)) {
                return cached;
            }
        }
        
        // Search in libs directory
        ScriptMetadata dependency = findDependencyInLibs(name, version);
        if (dependency != null) {
            dependencyCache.put(name.toLowerCase(), dependency);
            return dependency;
        }
        
        // Search in scripts directory
        dependency = findDependencyInScripts(name, version);
        if (dependency != null) {
            dependencyCache.put(name.toLowerCase(), dependency);
            return dependency;
        }
        
        throw new DependencyResolutionException("Dependency not found: " + dep + " required by " + 
                requester.getFullId());
    }
    
    /**
     * Find a dependency in the libs directory.
     */
    private ScriptMetadata findDependencyInLibs(String name, String version) {
        File[] files = libsDir.listFiles((dir, fileName) -> 
            fileName.toLowerCase().startsWith(name.toLowerCase() + "-") && 
            (fileName.endsWith(".jar") || fileName.endsWith(".zip"))
        );
        
        if (files == null || files.length == 0) {
            return null;
        }
        
        // Sort by version (newest first)
        Arrays.sort(files, (f1, f2) -> {
            String v1 = extractVersion(f1.getName(), name);
            String v2 = extractVersion(f2.getName(), name);
            return compareVersions(v2, v1); // Descending order
        });
        
        // Find first matching version
        for (File file : files) {
            String fileVersion = extractVersion(file.getName(), name);
            if (version.equals("*") || isVersionCompatible(fileVersion, version)) {
                try {
                    return ScriptMetadata.fromJar(file);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load library: " + file.getName(), e);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find a dependency in the scripts directory.
     */
    private ScriptMetadata findDependencyInScripts(String name, String version) {
        File scriptsDir = new File(plugin.getDataFolder(), "scripts");
        if (!scriptsDir.exists()) {
            return null;
        }
        
        try (Stream<Path> walk = Files.walk(scriptsDir.toPath())) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.equalsIgnoreCase(name + ".js") || 
                           fileName.equalsIgnoreCase(name + ".py") ||
                           fileName.equalsIgnoreCase(name + ".rb") ||
                           fileName.equalsIgnoreCase(name + ".lua");
                })
                .findFirst()
                .map(path -> {
                    try {
                        ScriptLanguage lang = ScriptLanguage.fromFileName(path.getFileName().toString());
                        return ScriptMetadata.fromFile(path.toFile(), lang);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load script: " + path, e);
                        return null;
                    }
                })
                .orElse(null);
                
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error searching for dependency: " + name, e);
            return null;
        }
    }
    
    /**
     * Extract version from a file name.
     */
    private String extractVersion(String fileName, String baseName) {
        // Remove base name and extension
        String version = fileName.substring(baseName.length());
        version = version.replaceAll("^[^0-9]+", ""); // Remove non-numeric prefix
        version = version.replaceAll("[^0-9.].*$", ""); // Remove everything after version
        return version.isEmpty() ? "1.0.0" : version;
    }
    
    /**
     * Check if a version is compatible with a version constraint.
     */
    private boolean isVersionCompatible(String version, String constraint) {
        if (constraint.equals("*")) {
            return true;
        }
        
        // Handle basic version constraints (>, >=, <, <=, =, !=)
        if (constraint.startsWith(">=")) {
            return compareVersions(version, constraint.substring(2)) >= 0;
        } else if (constraint.startsWith(">")) {
            return compareVersions(version, constraint.substring(1)) > 0;
        } else if (constraint.startsWith("<=")) {
            return compareVersions(version, constraint.substring(2)) <= 0;
        } else if (constraint.startsWith("<")) {
            return compareVersions(version, constraint.substring(1)) < 0;
        } else if (constraint.startsWith("!=")) {
            return compareVersions(version, constraint.substring(2)) != 0;
        } else if (constraint.startsWith("=") || constraint.startsWith("==")) {
            String v = constraint.startsWith("==") ? constraint.substring(2) : constraint.substring(1);
            return compareVersions(version, v) == 0;
        }
        
        // Default to exact match
        return version.equals(constraint);
    }
    
    /**
     * Compare two version strings.
     * @return negative if v1 < v2, positive if v1 > v2, 0 if equal
     */
    private int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        
        String[] parts1 = v1.split("\\.[-+]");
        String[] parts2 = v2.split("\\.[-+]");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int part2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            int cmp = Integer.compare(part1, part2);
            if (cmp != 0) {
                return cmp;
            }
        }
        
        return 0;
    }
    
    /**
     * Parse a version part (e.g., "1", "2-rc1").
     */
    private int parseVersionPart(String part) {
        // Remove any non-numeric suffix
        part = part.replaceAll("[^0-9].*$", "");
        try {
            return part.isEmpty() ? 0 : Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Get all installed libraries.
     */
    public List<File> getInstalledLibraries() {
        File[] files = libsDir.listFiles((dir, name) -> 
            name.endsWith(".jar") || name.endsWith(".zip")
        );
        
        return files != null ? Arrays.asList(files) : Collections.emptyList();
    }
    
    /**
     * Install a library from a file or URL.
     */
    public boolean installLibrary(File file) {
        if (!file.exists()) {
            return false;
        }
        
        try {
            // Extract metadata from JAR
            ScriptMetadata metadata = ScriptMetadata.fromJar(file);
            if (metadata == null) {
                return false;
            }
            
            // Create destination file
            String fileName = metadata.getId() + "-" + metadata.getVersion() + ".jar";
            File dest = new File(libsDir, fileName);
            
            // Copy file
            Files.copy(file.toPath(), dest.toPath());
            
            // Update cache
            dependencyCache.put(metadata.getId().toLowerCase(), metadata);
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to install library: " + file.getName(), e);
            return false;
        }
    }
    
    /**
     * Uninstall a library by name.
     */
    public boolean uninstallLibrary(String name) {
        File[] files = libsDir.listFiles((dir, fileName) -> 
            fileName.toLowerCase().startsWith(name.toLowerCase() + "-") && 
            (fileName.endsWith(".jar") || fileName.endsWith(".zip"))
        );
        
        if (files == null || files.length == 0) {
            return false;
        }
        
        boolean success = true;
        for (File file : files) {
            if (!file.delete()) {
                plugin.getLogger().warning("Failed to delete library: " + file.getName());
                success = false;
            } else {
                // Remove from cache
                String libName = file.getName().replaceAll("(-\\d+\\.\\d+\\.\\d+).*\\.(jar|zip)$", "");
                dependencyCache.remove(libName.toLowerCase());
            }
        }
        
        return success;
    }
    
    /**
     * Clear the dependency cache.
     */
    public void clearCache() {
        dependencyCache.clear();
    }
    
    /**
     * Exception thrown when dependency resolution fails.
     */
    public static class DependencyResolutionException extends Exception {
        public DependencyResolutionException(String message) {
            super(message);
        }
        
        public DependencyResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
