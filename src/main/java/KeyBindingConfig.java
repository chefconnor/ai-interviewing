import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class KeyBindingConfig {
    public enum Action {
        MOVE_UP("Move Up"),
        MOVE_DOWN("Move Down"),
        SLURP_PREVIOUS("Slurp Previous"),
        SUBMIT("Submit Selection"),
        CONTINUOUS_MODE("Toggle Continuous Mode"),
        CLEAR_SELECTION("Clear Selection");
        
        private final String displayName;
        
        Action(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public static class KeyBinding {
        private final int keyCode;
        private final Set<Integer> modifiers;
        private final String displayString;
        
        public KeyBinding(int keyCode, Set<Integer> modifiers) {
            this.keyCode = keyCode;
            this.modifiers = new HashSet<>(modifiers);
            this.displayString = buildDisplayString(keyCode, modifiers);
        }
        
        public boolean matches(int pressedKey, Set<Integer> pressedModifiers) {
            return keyCode == pressedKey && modifiers.equals(pressedModifiers);
        }
        
        public String getDisplayString() {
            return displayString;
        }
        
        public int getKeyCode() {
            return keyCode;
        }
        
        private static String buildDisplayString(int keyCode, Set<Integer> modifiers) {
            StringBuilder sb = new StringBuilder();
            
            // Build modifier string in consistent order
            if (modifiers.contains(NativeKeyEvent.VC_CONTROL)) {
                sb.append("CTRL+");
            }
            if (modifiers.contains(NativeKeyEvent.VC_ALT)) {
                sb.append("ALT+");
            }
            if (modifiers.contains(NativeKeyEvent.VC_META)) {
                sb.append("CMD+");
            }
            if (modifiers.contains(NativeKeyEvent.VC_SHIFT)) {
                sb.append("SHIFT+");
            }
            
            // Add the main key
            sb.append(NativeKeyEvent.getKeyText(keyCode));
            
            return sb.toString();
        }
    }
    
    private final Map<Action, KeyBinding> bindings = new HashMap<>();
    private static final String CONFIG_FILE = "keybindings.properties";
    
    public void setBinding(Action action, KeyBinding binding) {
        bindings.put(action, binding);
    }
    
    public KeyBinding getBinding(Action action) {
        return bindings.get(action);
    }
    
    public Action findAction(int keyCode, Set<Integer> modifiers) {
        for (Map.Entry<Action, KeyBinding> entry : bindings.entrySet()) {
            if (entry.getValue() != null && entry.getValue().matches(keyCode, modifiers)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public void saveToFile() {
        Properties props = new Properties();
        for (Map.Entry<Action, KeyBinding> entry : bindings.entrySet()) {
            KeyBinding binding = entry.getValue();
            if (binding != null) {
                props.setProperty(entry.getKey().name() + ".keyCode", String.valueOf(binding.keyCode));
                props.setProperty(entry.getKey().name() + ".modifiers", 
                    binding.modifiers.stream()
                        .map(String::valueOf)
                        .reduce((a, b) -> a + "," + b)
                        .orElse(""));
            }
        }
        
        try (OutputStream out = Files.newOutputStream(Paths.get(CONFIG_FILE))) {
            props.store(out, "Key Binding Configuration");
        } catch (IOException e) {
            System.err.println("Failed to save key bindings: " + e.getMessage());
        }
    }
    
    public void loadFromFile() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            return;
        }
        
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
            
            for (Action action : Action.values()) {
                String keyCodeStr = props.getProperty(action.name() + ".keyCode");
                String modifiersStr = props.getProperty(action.name() + ".modifiers");
                
                if (keyCodeStr != null) {
                    int keyCode = Integer.parseInt(keyCodeStr);
                    Set<Integer> modifiers = new HashSet<>();
                    
                    if (modifiersStr != null && !modifiersStr.isEmpty()) {
                        for (String mod : modifiersStr.split(",")) {
                            modifiers.add(Integer.parseInt(mod.trim()));
                        }
                    }
                    
                    bindings.put(action, new KeyBinding(keyCode, modifiers));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load key bindings: " + e.getMessage());
        }
    }
    
    public String getDisplayString(Action action) {
        KeyBinding binding = bindings.get(action);
        return binding != null ? binding.getDisplayString() : "Not Set";
    }
}