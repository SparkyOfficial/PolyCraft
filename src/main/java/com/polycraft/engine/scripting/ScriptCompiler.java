package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import com.polycraft.engine.security.ScriptSecurityPolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles compilation of script source code into executable classes.
 */
public class ScriptCompiler {
    
    private final PolyCraftEngine plugin;
    private final ScriptSecurityPolicy securityPolicy;
    private final JavaCompiler compiler;
    private final DiagnosticCollector<JavaFileObject> diagnostics;
    private final StandardJavaFileManager fileManager;
    private final List<String> compilerOptions;
    
    public ScriptCompiler(PolyCraftEngine plugin, ScriptSecurityPolicy securityPolicy) {
        this.plugin = plugin;
        this.securityPolicy = securityPolicy != null ? securityPolicy : new ScriptSecurityPolicy.Builder().build();
        
        // Get the system Java compiler
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available. Make sure to run with JDK, not JRE.");
        }
        
        this.diagnostics = new DiagnosticCollector<>();
        this.fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        
        // Set up compiler options
        this.compilerOptions = new ArrayList<>();
        
        // Add classpath from plugin class loader
        String classpath = getClasspath();
        if (classpath != null && !classpath.isEmpty()) {
            compilerOptions.add("-classpath");
            compilerOptions.add(classpath);
        }
        
        // Add additional compiler options
        compilerOptions.add("-Xlint:all");
        compilerOptions.add("-g"); // Generate all debugging info
        
        if (plugin.getConfig().getBoolean("debug.compiler.warningsAsErrors", false)) {
            compilerOptions.add("-Werror");
        }
    }
    
    /**
     * Compile Java source code into a class.
     * @param className The fully qualified class name
     * @param sourceCode The Java source code
     * @return The compiled class, or null if compilation failed
     */
    public Class<?> compile(String className, String sourceCode) {
        // Create a file object for the source code
        JavaFileObject sourceFile = new StringJavaFileObject(className, sourceCode);
        
        // Set up the compilation task
        Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(sourceFile);
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, // Writer for compiler output (null = System.err)
            fileManager,
            diagnostics,
            compilerOptions,
            null, // List of class names for annotation processing
            compilationUnits
        );
        
        // Run the compilation
        boolean success = task.call();
        
        // Check for errors
        if (!success) {
            // Log compilation errors
            for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String message = String.format(
                    "Error on line %d in %s: %s%n",
                    diagnostic.getLineNumber(),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "<unknown>",
                    diagnostic.getMessage(Locale.getDefault())
                );
                plugin.getLogger().severe(message);
            }
            return null;
        }
        
        // Load the compiled class
        try {
            // Create a class loader with the compiled classes
            ScriptClassLoader classLoader = new ScriptClassLoader(plugin, securityPolicy);
            
            // The class should be in the system class loader's classpath
            // If not, we would need to implement a custom ClassLoader to find it
            return Class.forName(className, true, classLoader);
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load compiled class: " + className, e);
            return null;
        }
    }
    
    /**
     * Get the classpath for compilation.
     */
    private String getClasspath() {
        // Get the classpath from the system property
        String classpath = System.getProperty("java.class.path");
        
        // Add the plugin's classpath
        try {
            // Get the plugin's classpath
            String pluginPath = plugin.getClass().getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            
            if (classpath == null || classpath.isEmpty()) {
                classpath = pluginPath;
            } else {
                classpath += File.pathSeparator + pluginPath;
            }
            
            // Add Bukkit/Spigot/Paper API to classpath
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.isEnabled()) {
                    String path = p.getClass().getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
                        .getPath();
                    
                    if (!classpath.contains(path)) {
                        classpath += File.pathSeparator + path;
                    }
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build classpath", e);
        }
        
        return classpath;
    }
    
    /**
     * Close the compiler and release resources.
     */
    public void close() {
        try {
            fileManager.close();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing file manager", e);
        }
    }
    
    /**
     * A file object that represents a string as source code.
     */
    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String sourceCode;
        
        protected StringJavaFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                  Kind.SOURCE);
            this.sourceCode = sourceCode;
        }
        
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }
}
