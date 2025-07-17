package com.polycraft.engine.scripting;

import com.polycraft.engine.PolyCraftEngine;
import org.bukkit.command.CommandSender;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;

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
        // Create context builder with appropriate settings
        Context context = null;
        try {
            context = Context.newBuilder()
                .allowIO(true)
                .allowAllAccess(true)
                .fileSystem(FileSystem.newDefaultFileSystem())
                .build();
                
            // Set up the global 'poly' object
            Value poly = context.eval(language, "({})");
            
            // Add basic utilities
            poly.putMember("log", new java.util.function.Consumer<String>() {
                @Override
                public void accept(String message) {
                    plugin.getLogger().info("[Eval] " + message);
                }
            });
                
            // Add sender reference if available
            if (sender != null) {
                poly.putMember("sender", sender);
            }
            
            // Add server reference
            poly.putMember("server", plugin.getServer());
            
            // Set the poly object in the context
            context.getBindings(language).putMember("poly", poly);
            
            return context;
        } catch (Exception e) {
            // Ensure context is closed if there's an error during setup
            if (context != null) {
                try {
                    context.close();
                } catch (Exception closeEx) {
                    // Ignore close exception
                }
            }
            throw e;
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
