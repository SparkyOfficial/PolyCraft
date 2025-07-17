package com.polycraft.engine.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Defines the security policy for a script.
 */
public class ScriptSecurityPolicy {
    private final boolean allowNativeAccess;
    private final boolean allowReflection;
    private final boolean allowFileSystemAccess;
    private final boolean allowNetworkAccess;
    private final Set<String> allowedPackages;
    private final Set<String> allowedClasses;
    
    private ScriptSecurityPolicy(Builder builder) {
        this.allowNativeAccess = builder.allowNativeAccess;
        this.allowReflection = builder.allowReflection;
        this.allowFileSystemAccess = builder.allowFileSystemAccess;
        this.allowNetworkAccess = builder.allowNetworkAccess;
        this.allowedPackages = Collections.unmodifiableSet(new HashSet<>(builder.allowedPackages));
        this.allowedClasses = Collections.unmodifiableSet(new HashSet<>(builder.allowedClasses));
    }
    
    /**
     * Check if native access is allowed.
     */
    public boolean isNativeAccessAllowed() {
        return allowNativeAccess;
    }
    
    /**
     * Check if reflection is allowed.
     */
    public boolean isReflectionAllowed() {
        return allowReflection;
    }
    
    /**
     * Check if file system access is allowed.
     */
    public boolean isFileSystemAccessAllowed() {
        return allowFileSystemAccess;
    }
    
    /**
     * Check if network access is allowed.
     */
    public boolean isNetworkAccessAllowed() {
        return allowNetworkAccess;
    }
    
    /**
     * Get the set of allowed package prefixes.
     * @return An unmodifiable set of allowed package prefixes
     */
    public Set<String> getAllowedPackages() {
        return allowedPackages;
    }
    
    /**
     * Get the set of allowed classes.
     */
    public Set<String> getAllowedClasses() {
        return allowedClasses;
    }
    
    /**
     * Check if a class is accessible under this policy.
     */
    public boolean isClassAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        
        // Check explicitly allowed classes
        if (allowedClasses.contains(className)) {
            return true;
        }
        
        // Check allowed packages
        for (String pkg : allowedPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptSecurityPolicy that = (ScriptSecurityPolicy) o;
        return allowNativeAccess == that.allowNativeAccess &&
               allowReflection == that.allowReflection &&
               allowFileSystemAccess == that.allowFileSystemAccess &&
               allowNetworkAccess == that.allowNetworkAccess &&
               allowedPackages.equals(that.allowedPackages) &&
               allowedClasses.equals(that.allowedClasses);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(
            allowNativeAccess, 
            allowReflection, 
            allowFileSystemAccess, 
            allowNetworkAccess, 
            allowedPackages, 
            allowedClasses
        );
    }
    
    @Override
    public String toString() {
        return "ScriptSecurityPolicy{" +
               "allowNativeAccess=" + allowNativeAccess +
               ", allowReflection=" + allowReflection +
               ", allowFileSystemAccess=" + allowFileSystemAccess +
               ", allowNetworkAccess=" + allowNetworkAccess +
               ", allowedPackages=" + allowedPackages +
               ", allowedClasses=" + allowedClasses +
               '}';
    }
    
    /**
     * Builder for creating ScriptSecurityPolicy instances.
     */
    public static class Builder {
        private boolean allowNativeAccess = false;
        private boolean allowReflection = false;
        private boolean allowFileSystemAccess = false;
        private boolean allowNetworkAccess = false;
        private final Set<String> allowedPackages = new HashSet<>();
        private final Set<String> allowedClasses = new HashSet<>();
        
        /**
         * Allow or disallow native access.
         */
        public Builder allowNativeAccess(boolean allow) {
            this.allowNativeAccess = allow;
            return this;
        }
        
        /**
         * Allow or disallow reflection.
         */
        public Builder allowReflection(boolean allow) {
            this.allowReflection = allow;
            return this;
        }
        
        /**
         * Allow or disallow file system access.
         */
        public Builder allowFileSystemAccess(boolean allow) {
            this.allowFileSystemAccess = allow;
            return this;
        }
        
        /**
         * Allow or disallow network access.
         */
        public Builder allowNetworkAccess(boolean allow) {
            this.allowNetworkAccess = allow;
            return this;
        }
        
        /**
         * Add allowed package prefixes.
         */
        public Builder allowedPackages(String... packages) {
            for (String pkg : packages) {
                if (pkg != null && !pkg.isEmpty()) {
                    this.allowedPackages.add(pkg.endsWith(".") ? pkg : (pkg + "."));
                }
            }
            return this;
        }
        
        /**
         * Add allowed classes.
         */
        public Builder allowedClasses(String... classes) {
            for (String cls : classes) {
                if (cls != null && !cls.isEmpty()) {
                    this.allowedClasses.add(cls);
                }
            }
            return this;
        }
        
        /**
         * Build the security policy.
         */
        public ScriptSecurityPolicy build() {
            // Add default allowed packages if none specified
            if (allowedPackages.isEmpty()) {
                allowedPackages.add("java.lang.");
            }
            
            return new ScriptSecurityPolicy(this);
        }
    }
}
