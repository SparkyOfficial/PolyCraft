package com.polycraft.engine.scripting;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Context;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a supported scripting language in the PolyCraft Engine.
 */
public enum ScriptLanguage {
    JAVASCRIPT("js", "js", "application/javascript"),
    PYTHON("python", "py", "application/python"),
    RUBY("ruby", "rb", "application/x-ruby"),
    LLVM("llvm", "llvm", "application/llvm"),
    WASM("wasm", "wasm", "application/wasm"),
    JAVA("java", "java", "text/x-java-source"),
    CSHARP("csharp", "cs", "text/x-csharp"),
    UNKNOWN("", "", "");

    private static final Map<String, ScriptLanguage> BY_EXTENSION = initExtensionMap();
    private static final Map<String, ScriptLanguage> BY_MIME_TYPE = initMimeTypeMap();
    private static final Set<String> SUPPORTED_LANGUAGES = initSupportedLanguages();

    private static Set<String> initSupportedLanguages() {
        Set<String> supported = new HashSet<>();
        try (Context context = Context.newBuilder().build()) {
            supported = new HashSet<>(context.getEngine().getLanguages().keySet());
        } catch (Exception e) {
            // GraalVM not available or error initializing
            supported.add("js"); // At least JavaScript is supported
        }
        return Collections.unmodifiableSet(supported);
    }

    private static Map<String, ScriptLanguage> initExtensionMap() {
        Map<String, ScriptLanguage> byExt = new HashMap<>();
        // Use direct enum constants to avoid initialization issues
        byExt.put("js", JAVASCRIPT);
        byExt.put("py", PYTHON);
        byExt.put("rb", RUBY);
        byExt.put("llvm", LLVM);
        byExt.put("wasm", WASM);
        byExt.put("java", JAVA);
        byExt.put("cs", CSHARP);
        return Collections.unmodifiableMap(byExt);
    }

    private static Map<String, ScriptLanguage> initMimeTypeMap() {
        Map<String, ScriptLanguage> byMime = new HashMap<>();
        // Use direct enum constants to avoid initialization issues
        byMime.put("application/javascript", JAVASCRIPT);
        byMime.put("application/python", PYTHON);
        byMime.put("application/x-ruby", RUBY);
        byMime.put("application/llvm", LLVM);
        byMime.put("application/wasm", WASM);
        byMime.put("text/x-java-source", JAVA);
        byMime.put("text/x-csharp", CSHARP);
        return Collections.unmodifiableMap(byMime);
    }

    private final String graalId;
    private final String fileExtension;
    private final String mimeType;
    private boolean graalSupported;

    ScriptLanguage(String graalId, String fileExtension, String mimeType) {
        this.graalId = graalId;
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
    }
    
    // Initialize graalSupported after SUPPORTED_LANGUAGES is initialized
    static {
        for (ScriptLanguage lang : values()) {
            lang.graalSupported = SUPPORTED_LANGUAGES.contains(lang.graalId);
        }
    }

    /**
     * Get the file extension for this language (without leading dot).
     */
    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Get the GraalVM language ID.
     */
    public String getGraalId() {
        return graalId;
    }

    /**
     * Get the MIME type for this language.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Get the display name of the language.
     */
    public String getDisplayName() {
        // Convert enum name to display name (e.g., JAVASCRIPT -> "JavaScript")
        String name = name();
        if (name.isEmpty()) return "Unknown";
        return name.substring(0, 1).toUpperCase() + 
               name.substring(1).toLowerCase();
    }

    /**
     * Check if this language is supported by the current GraalVM installation.
     */
    public boolean isGraalSupported() {
        return graalSupported;
    }

    /**
     * Create a new bindings object for this language.
     * The actual type depends on the language implementation.
     */
    public Object createBindings() {
        // Default implementation returns a new HashMap
        // Individual language implementations can override this if needed
        return new HashMap<>();
    }

    /**
     * Create a Source object from a file for this language.
     */
    public Source createSource(File file) throws IOException {
        if (this == UNKNOWN) {
            throw new UnsupportedOperationException("Cannot create source for unknown language");
        }
        
        String mimeType = getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = null; // Let GraalVM detect the MIME type
        }
        
        return Source.newBuilder(graalId, file)
            .mimeType(mimeType)
            .name(file.getName())
            .build();
    }

    /**
     * Create a Source object from a string for this language.
     */
    public Source createSource(String code, String name) {
        if (this == UNKNOWN) {
            throw new UnsupportedOperationException("Cannot create source for unknown language");
        }
        
        String mimeType = getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = null; // Let GraalVM detect the MIME type
        }
        
        return Source.newBuilder(graalId, code, name != null ? name : "<script>")
            .mimeType(mimeType)
            .buildLiteral();
    }

    /**
     * Get the language from a file extension.
     */
    public static ScriptLanguage fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return UNKNOWN;
        }
        
        // Remove leading dot if present
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        
        return BY_EXTENSION.getOrDefault(extension.toLowerCase(), UNKNOWN);
    }

    /**
     * Determines the script language from a file name based on its extension.
     * @param fileName The name of the file
     * @return The ScriptLanguage corresponding to the file extension, or UNKNOWN if unknown
     */
    public static ScriptLanguage fromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return UNKNOWN;
        }
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return UNKNOWN;
        }
        
        String extension = fileName.substring(dotIndex).toLowerCase();
        return BY_EXTENSION.getOrDefault(extension.substring(1), UNKNOWN);
    }

    /**
     * Get the language from a GraalVM language ID.
     */
    public boolean isSupported() {
        if (this == UNKNOWN) {
            return false;
        }
        
        try {
            // Try to create a context for this language
            Context context = Context.newBuilder(graalId).build();
            context.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get all supported languages in the current environment.
     * @return An array of supported languages
     */
    public static ScriptLanguage[] getSupportedLanguages() {
        return Arrays.stream(values())
            .filter(ScriptLanguage::isSupported)
            .toArray(ScriptLanguage[]::new);
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
}
