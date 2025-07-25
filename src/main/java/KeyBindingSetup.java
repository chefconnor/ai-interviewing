import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class KeyBindingSetup implements NativeKeyListener {
    private final KeyBindingConfig config;
    private KeyBindingConfig.Action currentAction;
    private CountDownLatch latch;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean captureComplete = false;
    
    // Track which modifiers are pressed
    private final Set<Integer> modifiers = new HashSet<>();
    
    public KeyBindingSetup(KeyBindingConfig config) {
        this.config = config;
    }
    
    public void setupBindings() throws NativeHookException {
        // Don't register native hook here - it's already registered in Main
        GlobalScreen.addNativeKeyListener(this);
        
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("KEY BINDING SETUP");
            System.out.println("=".repeat(80));
            System.out.println("Press your desired key combination for each action.");
            System.out.println("You can use modifiers: CMD, ALT, SHIFT, CTRL");
            System.out.println("Note: Left and right modifiers are distinguished (e.g., SHIFT_L vs SHIFT_R)\n");
            
            // Setup each action
            for (KeyBindingConfig.Action action : KeyBindingConfig.Action.values()) {
                captureBinding(action);
            }
            
            // Save configuration
            config.saveToFile();
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("KEY BINDING SUMMARY");
            System.out.println("=".repeat(80));
            for (KeyBindingConfig.Action action : KeyBindingConfig.Action.values()) {
                System.out.printf("%-25s: %s%n", 
                    action.getDisplayName(), 
                    config.getDisplayString(action));
            }
            System.out.println("=".repeat(80) + "\n");
            
        } finally {
            GlobalScreen.removeNativeKeyListener(this);
        }
    }
    
    private void captureBinding(KeyBindingConfig.Action action) {
        currentAction = action;
        latch = new CountDownLatch(1);
        captureComplete = false;
        pressedKeys.clear();
        modifiers.clear();
        
        System.out.printf("Press key combination for '%s': ", action.getDisplayName());
        System.out.flush();
        
        try {
            // Wait for key binding with timeout
            if (!latch.await(10, TimeUnit.SECONDS)) {
                System.out.println("Timeout - skipping this binding");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted - skipping this binding");
            return;
        }
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (captureComplete) return;
        
        int keyCode = e.getKeyCode();
        pressedKeys.add(keyCode);
        
        // Track modifiers with left/right distinction
        switch (keyCode) {
            case NativeKeyEvent.VC_SHIFT:
            case NativeKeyEvent.VC_CONTROL:
            case NativeKeyEvent.VC_ALT:
            case NativeKeyEvent.VC_META:
                modifiers.add(keyCode);
                break;
            default:
                // Non-modifier key pressed - this is our action key
                if (!modifiers.isEmpty() || !isModifierKey(keyCode)) {
                    KeyBindingConfig.KeyBinding binding = 
                        new KeyBindingConfig.KeyBinding(keyCode, new HashSet<>(modifiers));
                    config.setBinding(currentAction, binding);
                    System.out.println(binding.getDisplayString());
                    captureComplete = true;
                    latch.countDown();
                }
                break;
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        pressedKeys.remove(keyCode);
        modifiers.remove(keyCode);
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used
    }
    
    private boolean isModifierKey(int keyCode) {
        return keyCode == NativeKeyEvent.VC_SHIFT ||
               keyCode == NativeKeyEvent.VC_CONTROL ||
               keyCode == NativeKeyEvent.VC_ALT ||
               keyCode == NativeKeyEvent.VC_META;
    }
}