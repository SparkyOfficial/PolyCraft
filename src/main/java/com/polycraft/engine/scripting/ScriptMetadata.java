package com.polycraft.engine.scripting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Represents metadata for a script, including its name, version, dependencies, and other attributes.
 */
public class ScriptMetadata {
    
    private static final String METADATA_FILE = "script.yml";
    private static final String METADATA_JSON = "script.json";
    private static final String METADATA_PACKAGE_JSON = "package.json";
    
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String main;
    private final List<String> authors;
    private final String website;
    private final List<String> depends;
    private final List<String> softDepends;
    private final Map<String, Object> data;
    private final ScriptLanguage language;
    private final File scriptFile;
    private final File dataFolder;
    private final boolean valid;
    
    private ScriptMetadata(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.main = builder.main;
        this.authors = Collections.unmodifiableList(builder.authors);
        this.website = builder.website;
        this.depends = Collections.unmodifiableList(builder.depends);
        this.softDepends = Collections.unmodifiableList(builder.softDepends);
        this.data = Collections.unmodifiableMap(builder.data);
        this.language = builder.language;
        this.scriptFile = builder.scriptFile;
        this.dataFolder = builder.dataFolder;
        this.valid = builder.valid;
    }
    
    /**
     * Load script metadata from a file.
     */
    public static ScriptMetadata fromFile(File file, ScriptLanguage language) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Script file does not exist: " + file);
        }
        
        Builder builder = new Builder(file, language);
        
        try {
            // Try to load from script.yml
            File scriptYml = new File(file.getParentFile(), METADATA_FILE);
            if (scriptYml.exists()) {
                return fromYaml(scriptYml, file, language);
            }
            
            // Try to load from script.json
            File scriptJson = new File(file.getParentFile(), METADATA_JSON);
            if (scriptJson.exists()) {
                return fromJson(scriptJson, file, language);
            }
            
            // For Node.js packages, try package.json
            if (language == ScriptLanguage.JAVASCRIPT) {
                File packageJson = new File(file.getParentFile(), METADATA_PACKAGE_JSON);
                if (packageJson.exists()) {
                    return fromPackageJson(packageJson, file, language);
                }
            }
            
            // If no metadata file found, use defaults based on file name
            String fileName = file.getName();
            String scriptName = fileName.substring(0, fileName.lastIndexOf('.'));
            
            return builder
                .id(scriptName.toLowerCase().replace(" ", "_"))
                .name(scriptName)
                .version("1.0.0")
                .description("A " + language.getDisplayName() + " script")
                .main(fileName)
                .author("Unknown")
                .valid(true)
                .build();
            
        } catch (Exception e) {
            PolyCraftEngine.getInstance().getLogger().log(
                Level.WARNING,
                "Failed to load metadata for script: " + file.getName(),
                e
            );
            
            // Return invalid metadata with basic info
            return builder
                .id("invalid_" + System.currentTimeMillis())
                .name("Invalid Script")
                .version("0.0.0")
                .description("Failed to load script metadata: " + e.getMessage())
                .valid(false)
                .build();
        }
    }
    
    /**
     * Load metadata from a YAML file.
     */
    private static ScriptMetadata fromYaml(File yamlFile, File scriptFile, ScriptLanguage language) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(yamlFile);
        
        Builder builder = new Builder(scriptFile, language);
        
        return builder
            .id(yaml.getString("id", ""))
            .name(yaml.getString("name", scriptFile.getName()))
            .version(yaml.getString("version", "1.0.0"))
            .description(yaml.getString("description", ""))
            .main(yaml.getString("main", scriptFile.getName()))
            .authors(yaml.contains("author") ? 
                Collections.singletonList(yaml.getString("author")) : 
                yaml.getStringList("authors"))
            .website(yaml.getString("website", ""))
            .depends(yaml.getStringList("depend"))
            .softDepends(yaml.getStringList("softdepend"))
            .data(yaml.getValues(true))
            .valid(true)
            .build();
    }
    
    /**
     * Load metadata from a JSON file.
     */
    private static ScriptMetadata fromJson(File jsonFile, File scriptFile, ScriptLanguage language) throws IOException {
        Gson gson = new Gson();
        try (InputStream is = Files.newInputStream(jsonFile.toPath())) {
            JsonObject json = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            
            Builder builder = new Builder(scriptFile, language);
            
            // Handle authors as either a string or an array
            List<String> authors = new ArrayList<>();
            if (json.has("author")) {
                if (json.get("author").isJsonPrimitive()) {
                    authors.add(json.get("author").getAsString());
                } else if (json.get("author").isJsonArray()) {
                    json.getAsJsonArray("author")
                        .forEach(e -> authors.add(e.getAsString()));
                }
            } else if (json.has("authors")) {
                json.getAsJsonArray("authors")
                    .forEach(e -> authors.add(e.getAsString()));
            }
            
            // Handle dependencies
            List<String> depends = new ArrayList<>();
            List<String> softDepends = new ArrayList<>();
            
            if (json.has("dependencies")) {
                json.getAsJsonObject("dependencies")
                    .entrySet()
                    .forEach(e -> depends.add(e.getKey() + "@" + e.getValue().getAsString()));
            }
            
            if (json.has("devDependencies")) {
                json.getAsJsonObject("devDependencies")
                    .entrySet()
                    .forEach(e -> softDepends.add(e.getKey() + "@" + e.getValue().getAsString()));
            }
            
            // Extract data
            Map<String, Object> data = new HashMap<>();
            json.entrySet().forEach(entry -> {
                data.put(entry.getKey(), entry.getValue().toString());
            });
            
            return builder
                .id(json.has("id") ? json.get("id").getAsString() : "")
                .name(json.has("name") ? json.get("name").getAsString() : scriptFile.getName())
                .version(json.has("version") ? json.get("version").getAsString() : "1.0.0")
                .description(json.has("description") ? json.get("description").getAsString() : "")
                .main(json.has("main") ? json.get("main").getAsString() : scriptFile.getName())
                .authors(authors)
                .website(json.has("website") ? json.get("website").getAsString() : "")
                .depends(depends)
                .softDepends(softDepends)
                .data(data)
                .valid(true)
                .build();
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in metadata file: " + jsonFile, e);
        }
    }
    
    /**
     * Load metadata from a package.json file (Node.js).
     */
    private static ScriptMetadata fromPackageJson(File packageJson, File scriptFile, ScriptLanguage language) throws IOException {
        ScriptMetadata metadata = fromJson(packageJson, scriptFile, language);
        
        // For Node.js packages, the main file should be relative to the package root
        String mainFile = metadata.getMain();
        if (mainFile != null && !mainFile.isEmpty()) {
            File mainJs = new File(packageJson.getParentFile(), mainFile);
            if (!mainJs.exists()) {
                throw new IOException("Main file not found: " + mainJs.getAbsolutePath());
            }
        }
        
        return metadata;
    }
    
    /**
     * Extract metadata from a JAR file.
     */
    public static ScriptMetadata fromJar(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            // Look for script.yml or script.json in the JAR
            JarEntry ymlEntry = jar.getJarEntry(METADATA_FILE);
            if (ymlEntry != null) {
                try (InputStream is = jar.getInputStream(ymlEntry)) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                    
                    // TODO: Extract script files from JAR
                    
                    return new Builder(jarFile, ScriptLanguage.JAVA)
                        .id(yaml.getString("id", ""))
                        .name(yaml.getString("name", jarFile.getName()))
                        .version(yaml.getString("version", "1.0.0"))
                        .description(yaml.getString("description", ""))
                        .main(yaml.getString("main", ""))
                        .authors(yaml.getStringList("authors"))
                        .website(yaml.getString("website", ""))
                        .depends(yaml.getStringList("depend"))
                        .softDepends(yaml.getStringList("softdepend"))
                        .data(yaml.getValues(true))
                        .valid(true)
                        .build();
                } catch (Exception e) {
                    throw new IOException("Failed to load script.yml from JAR", e);
                }
            }
            
            // TODO: Handle other metadata formats in JAR
            
            throw new IOException("No valid metadata found in JAR file");
        }
    }
    
    // Getters
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getMain() {
        return main;
    }
    
    public List<String> getAuthors() {
        return authors;
    }
    
    public String getWebsite() {
        return website;
    }
    
    public List<String> getDepends() {
        return depends;
    }
    
    public List<String> getSoftDepends() {
        return softDepends;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public ScriptLanguage getLanguage() {
        return language;
    }
    
    public File getScriptFile() {
        return scriptFile;
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    /**
     * Get the full script ID including version (name@version).
     */
    public String getFullId() {
        return id + "@" + version;
    }
    
    /**
     * Check if this script depends on another script.
     */
    public boolean dependsOn(String scriptId) {
        return depends.stream().anyMatch(dep -> dep.startsWith(scriptId + "@")) ||
               softDepends.stream().anyMatch(dep -> dep.startsWith(scriptId + "@"));
    }
    
    /**
     * Get the required version of a dependency, or null if not a dependency.
     */
    public String getDependencyVersion(String scriptId) {
        for (String dep : depends) {
            if (dep.startsWith(scriptId + "@")) {
                return dep.substring(scriptId.length() + 1);
            }
        }
        
        for (String dep : softDepends) {
            if (dep.startsWith(scriptId + "@")) {
                return dep.substring(scriptId.length() + 1);
            }
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return "ScriptMetadata{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", version='" + version + '\'' +
               ", language=" + language +
               ", valid=" + valid +
               '}';
    }
    
    /**
     * Builder for ScriptMetadata.
     */
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description = "";
        private String main;
        private final List<String> authors = new ArrayList<>();
        private String website = "";
        private final List<String> depends = new ArrayList<>();
        private final List<String> softDepends = new ArrayList<>();
        private final Map<String, Object> data = new HashMap<>();
        private final ScriptLanguage language;
        private final File scriptFile;
        private File dataFolder;
        private boolean valid = false;
        
        public Builder(File scriptFile, ScriptLanguage language) {
            this.scriptFile = scriptFile;
            this.language = language;
            
            // Default data folder is a folder with the same name as the script file
            String fileName = scriptFile.getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            this.dataFolder = new File(scriptFile.getParentFile(), baseName);
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }
        
        public Builder main(String main) {
            this.main = main;
            return this;
        }
        
        public Builder author(String author) {
            if (author != null && !author.isEmpty()) {
                this.authors.add(author);
            }
            return this;
        }
        
        public Builder authors(Collection<String> authors) {
            if (authors != null) {
                authors.forEach(this::author);
            }
            return this;
        }
        
        public Builder website(String website) {
            this.website = website != null ? website : "";
            return this;
        }
        
        public Builder depends(Collection<String> depends) {
            if (depends != null) {
                this.depends.addAll(depends);
            }
            return this;
        }
        
        public Builder softDepends(Collection<String> softDepends) {
            if (softDepends != null) {
                this.softDepends.addAll(softDepends);
            }
            return this;
        }
        
        public Builder data(Map<String, Object> data) {
            if (data != null) {
                this.data.putAll(data);
            }
            return this;
        }
        
        public Builder dataFolder(File dataFolder) {
            this.dataFolder = dataFolder;
            return this;
        }
        
        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }
        
        public ScriptMetadata build() {
            // Generate ID from name if not set
            if (id == null || id.isEmpty()) {
                id = name != null ? 
                    name.toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_-]", "") : 
                    "script_" + System.currentTimeMillis();
            }
            
            // Set default name from file name if not set
            if (name == null || name.isEmpty()) {
                name = scriptFile.getName();
                if (name.contains(".")) {
                    name = name.substring(0, name.lastIndexOf('.'));
                }
            }
            
            // Set default version if not set
            if (version == null || version.isEmpty()) {
                version = "1.0.0";
            }
            
            // Set main script file if not set
            if (main == null || main.isEmpty()) {
                main = scriptFile.getName();
            }
            
            // Ensure data folder exists
            if (dataFolder != null && !dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            return new ScriptMetadata(this);
        }
    }
}
