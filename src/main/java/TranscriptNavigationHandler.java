import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TranscriptNavigationHandler implements NativeKeyListener {
    
    private final TranscriptBuffer buffer;
    private final Consumer<String> submitToLLM;
    private final KeyBindingConfig config;
    private final Set<Integer> pressedKeys = new HashSet<>();
    
    // Track currently pressed action to prevent repeats
    private KeyBindingConfig.Action currentAction = null;
    
    public TranscriptNavigationHandler(TranscriptBuffer buffer, Consumer<String> submitToLLM, KeyBindingConfig config) {
        this.buffer = buffer;
        this.submitToLLM = submitToLLM;
        this.config = config;
    }
    
    public void register() throws NativeHookException {
        GlobalScreen.addNativeKeyListener(this);
        System.out.println("\nTranscript navigation hotkeys registered:");
        for (KeyBindingConfig.Action action : KeyBindingConfig.Action.values()) {
            System.out.printf("  %-25s: %s%n", 
                action.getDisplayName(), 
                config.getDisplayString(action));
        }
    }
    
    public void unregister() {
        GlobalScreen.removeNativeKeyListener(this);
    }
    
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        pressedKeys.add(keyCode);
        
        // Build current modifier set
        Set<Integer> modifiers = new HashSet<>();
        for (int key : pressedKeys) {
            if (isModifierKey(key)) {
                modifiers.add(key);
            }
        }
        
        // Find matching action
        KeyBindingConfig.Action action = config.findAction(keyCode, modifiers);
        
        // Handle MUTE_WHILE_HELD specially
        if (action == KeyBindingConfig.Action.MUTE_WHILE_HELD) {
            Main.setMuted(true);
            currentAction = action;
        }
        // Execute other actions if not already pressed
        else if (action != null && action != currentAction) {
            currentAction = action;
            executeAction(action);
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        pressedKeys.remove(keyCode);
        
        // Reset current action if the main key was released
        if (currentAction != null) {
            KeyBindingConfig.KeyBinding binding = config.getBinding(currentAction);
            if (binding != null && binding.getKeyCode() == keyCode) {
                // If releasing the mute key, unmute
                if (currentAction == KeyBindingConfig.Action.MUTE_WHILE_HELD) {
                    Main.setMuted(false);
                }
                currentAction = null;
            }
        }
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used
    }
    
    private void executeAction(KeyBindingConfig.Action action) {
        switch (action) {
            case MOVE_UP:
                buffer.moveUp();
                break;
            case MOVE_DOWN:
                buffer.moveDown();
                break;
            case SLURP_PREVIOUS:
                buffer.slurpPrevious();
                break;
            case SUBMIT:
                submitSelection();
                break;
            case CONTINUOUS_MODE:
                buffer.toggleContinuousMode();
                break;
            case CLEAR_SELECTION:
                buffer.clearSelection();
                break;
        }
    }
    
    private void submitSelection() {
        String selection = buffer.getSelection();
        if (selection.isEmpty()) {
            System.out.println("\n[No selection to submit]");
            return;
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUBMITTING TO LLM:");
        System.out.println("=".repeat(80));
        System.out.println(selection);
        System.out.println("=".repeat(80) + "\n");
        
        // Submit to LLM
        if (submitToLLM != null) {
            submitToLLM.accept(selection);
        }
    }
    
    private boolean isModifierKey(int keyCode) {
        return keyCode == NativeKeyEvent.VC_SHIFT ||
               keyCode == NativeKeyEvent.VC_CONTROL ||
               keyCode == NativeKeyEvent.VC_ALT ||
               keyCode == NativeKeyEvent.VC_META;
    }
}