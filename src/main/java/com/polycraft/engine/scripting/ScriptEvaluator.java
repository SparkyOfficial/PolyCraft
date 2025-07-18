package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.command.CommandSender;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

public class ScriptEvaluator {
    
    private final PolyCraftEngine plugin;
    
    public ScriptEvaluator(PolyCraftEngine plugin) {
        this.plugin = plugin;
    }
    
    public Object evaluateCode(String language, String code, CommandSender sender) throws Exception {
        String langId = getLanguageId(language);
        if (langId == null) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        
        try (Context context = createContext(langId, sender)) {
            Source source = Source.newBuilder(langId, new StringReader(code), "<eval>").build();
            Value result = context.eval(source);
            
            if (result != null && !result.isNull()) {
                if (result.isHostObject()) {
                    return result.asHostObject();
                } else if (result.hasArrayElements()) {
                    return result.as(List.class);
                } else if (result.hasMembers()) {
                    return result.as(Map.class);
                } else if (result.isString()) {
                    return result.asString();
                } else if (result.isNumber()) {
                    return result.as(Number.class);
                } else if (result.isBoolean()) {
                    return result.asBoolean();
                }
                return result.toString();
            }
            return null;
        }
    }
    
    private Context createContext(String language, CommandSender sender) {
        // 1. Get the shared engine from the main class, same as for scripts
        Engine sharedEngine = plugin.getGraalEngine();
        if (sharedEngine == null) {
            throw new IllegalStateException("Cannot create eval context: Shared GraalVM Engine is not available.");
        }
        
        try {
            // 2. Create a context builder with common options
            Context.Builder builder = Context.newBuilder(language)
                .engine(sharedEngine)
                .allowAllAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .allowIO(IOAccess.ALL)
                .allowCreateThread(true)
                .allowNativeAccess(true)
                .allowCreateProcess(true)
                .allowHostClassLoading(true)
                .allowExperimentalOptions(true);
            
            // 3. Apply language-specific options
            if ("js".equals(language)) {
                builder.option("js.ecmascript-version", "2022");
            } else if ("python".equals(language)) {
                // These options will only be applied when Python support is added
                builder.option("python.ForceImportSite", "true");
                // Uncomment and set path if needed:
                // builder.option("python.Executable", "/path/to/python");
                builder.option("python.PosixModulePath", "");
                builder.option("python.VirtualEnv", "");
            }
            
            // 4. Build the context
            Context context = builder.build();
                
            // 5. Get access to the script's global variables
            Value bindings = context.getBindings(language);
            
            // 6. Pass the ENTIRE PolyAPI as 'poly' variable
            bindings.putMember("poly", plugin.getPolyAPI());
            
            // 7. Pass sender as a SEPARATE global variable 'sender'
            if (sender != null) {
                bindings.putMember("sender", sender);
            }
            
            // 8. Pass 'server' for convenience
            bindings.putMember("server", plugin.getServer());
            
            return context;
                
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, 
                "Failed to create context for language: " + language, e);
            throw new RuntimeException("Failed to create eval context", e);
        }
    }
    
    private String getLanguageId(String language) {
        if (language == null) {
            return null;
        }
        
        // Map language aliases to their GraalVM language IDs
        switch (language.toLowerCase()) {
            case "js":
            case "javascript":
                return "js";
            case "py":
            case "python":
                return "python";
            case "java":
                return "java";
            case "cs":
            case "csharp":
                return "csharp";
            case "rb":
            case "ruby":
                return "ruby";
            case "llvm":
                return "llvm";
            case "wasm":
                return "wasm";
            default:
                return null;
        }
    }
}
