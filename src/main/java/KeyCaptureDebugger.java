import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeyCaptureDebugger implements NativeKeyListener {
    private final Set<Integer> pressedKeys = new HashSet<>();
    private int keyPressCount = 0;
    private int keyReleaseCount = 0;
    private long startTime;
    
    public static void main(String[] args) {
        System.out.println("=== Key Capture Debugger ===\n");
        
        // System information
        System.out.println("System Information:");
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("  Java: " + System.getProperty("java.version"));
        System.out.println("  JNativeHook: Present (version info in library)");
        System.out.println();
        
        // Disable JNativeHook logging for cleaner output
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);
        
        KeyCaptureDebugger debugger = new KeyCaptureDebugger();
        
        try {
            // Check if GlobalScreen is already registered
            boolean alreadyRegistered = false;
            try {
                // Try to add a dummy listener - if it works, GlobalScreen is already registered
                GlobalScreen.addNativeKeyListener(debugger);
                GlobalScreen.removeNativeKeyListener(debugger);
                alreadyRegistered = true;
                System.out.println("✓ GlobalScreen is already registered");
            } catch (Exception e) {
                System.out.println("✗ GlobalScreen is not registered");
            }
            
            // Register if needed
            if (!alreadyRegistered) {
                System.out.println("Attempting to register GlobalScreen...");
                GlobalScreen.registerNativeHook();
                System.out.println("✓ GlobalScreen registered successfully");
            }
            
            // Add the debugger as a listener
            GlobalScreen.addNativeKeyListener(debugger);
            System.out.println("✓ Key listener added successfully\n");
            
            debugger.startTime = System.currentTimeMillis();
            
            System.out.println("Key Capture Test Started");
            System.out.println("========================");
            System.out.println("Press keys to test capture functionality.");
            System.out.println("Press CTRL+C to exit.\n");
            System.out.println("Monitoring keyboard events...\n");
            
            // Keep running
            while (true) {
                Thread.sleep(100);
                
                // Print status every 10 seconds
                long elapsed = System.currentTimeMillis() - debugger.startTime;
                if (elapsed > 0 && elapsed % 10000 < 100) {
                    System.out.println(String.format("\n[Status] Running for %d seconds - Captured %d key presses, %d key releases",
                            elapsed / 1000, debugger.keyPressCount, debugger.keyReleaseCount));
                }
            }
            
        } catch (NativeHookException e) {
            System.err.println("\n✗ FAILED to register native hook!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("Error Code: " + e.getCode());
            
            System.err.println("\nPossible solutions:");
            if (e.getMessage().contains("Failed to enable access")) {
                System.err.println("1. Grant accessibility permissions:");
                System.err.println("   System Preferences > Security & Privacy > Privacy > Accessibility");
                System.err.println("   Add your IDE/Terminal to the allowed apps list");
            }
            System.err.println("2. Check if another instance is already capturing keys");
            System.err.println("3. Try running with elevated privileges (sudo)");
            System.err.println("4. Restart your IDE/Terminal after granting permissions");
            
        } catch (InterruptedException e) {
            System.out.println("\nProgram interrupted");
        } finally {
            try {
                GlobalScreen.removeNativeKeyListener(debugger);
                if (!alreadyRegistered) {
                    GlobalScreen.unregisterNativeHook();
                }
                System.out.println("\nCleanup completed");
            } catch (Exception e) {
                System.err.println("Error during cleanup: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        keyPressCount++;
        int keyCode = e.getKeyCode();
        pressedKeys.add(keyCode);
        
        System.out.println(String.format("[PRESS] Key: %s (Code: %d) | Currently pressed: %d keys | Total presses: %d",
                NativeKeyEvent.getKeyText(keyCode), keyCode, pressedKeys.size(), keyPressCount));
        
        // Show modifier keys
        if (isModifierKey(keyCode)) {
            System.out.println("        ^ Modifier key detected");
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        keyReleaseCount++;
        int keyCode = e.getKeyCode();
        pressedKeys.remove(keyCode);
        
        System.out.println(String.format("[RELEASE] Key: %s (Code: %d) | Currently pressed: %d keys",
                NativeKeyEvent.getKeyText(keyCode), keyCode, pressedKeys.size()));
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used for debugging
    }
    
    private boolean isModifierKey(int keyCode) {
        return keyCode == NativeKeyEvent.VC_SHIFT ||
               keyCode == NativeKeyEvent.VC_CONTROL ||
               keyCode == NativeKeyEvent.VC_ALT ||
               keyCode == NativeKeyEvent.VC_META;
    }
    
    private static boolean alreadyRegistered = false;
}