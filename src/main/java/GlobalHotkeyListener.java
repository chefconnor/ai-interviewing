import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalHotkeyListener implements NativeKeyListener {
    
    // Track currently pressed keys
    private final Set<Integer> pressedKeys = new HashSet<>();
    
    // Define the hotkey combination: Alt+Ctrl+Shift+W
    private static final int[] HOTKEY_COMBINATION = {
        NativeKeyEvent.VC_ALT,
        NativeKeyEvent.VC_CONTROL,
        NativeKeyEvent.VC_SHIFT,
        NativeKeyEvent.VC_W
    };
    
    public GlobalHotkeyListener() {
        // Disable JNativeHook logging to avoid console spam
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
    }
    
    public void register() throws NativeHookException {
        // Don't register GlobalScreen here - it should already be registered in Main
        GlobalScreen.addNativeKeyListener(this);
        System.out.println("Global hotkey listener registered. Press Alt+Ctrl+Shift+W to trigger.");
    }
    
    public void unregister() throws NativeHookException {
        GlobalScreen.removeNativeKeyListener(this);
        // Don't unregister GlobalScreen here - let Main handle it
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        pressedKeys.add(keyCode);
        
        // Check if all keys in the combination are pressed
        if (isHotkeyPressed()) {
            System.out.println("User has pressed 'Alt+Ctrl+Shift+W'");
            // You can trigger any action here
            onHotkeyPressed();
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used for hotkey detection
    }
    
    private boolean isHotkeyPressed() {
        for (int keyCode : HOTKEY_COMBINATION) {
            if (!pressedKeys.contains(keyCode)) {
                return false;
            }
        }
        return true;
    }
    
    // Override this method to customize what happens when the hotkey is pressed
    protected void onHotkeyPressed() {
        // This method can be overridden to perform custom actions
    }
}