import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeyCaptureTest implements NativeKeyListener {
    public static void main(String[] args) {
        System.out.println("Starting Key Capture Test...");
        
        // Disable JNativeHook logging
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        
        try {
            // Check if GlobalScreen is already registered
            System.out.println("Attempting to register GlobalScreen...");
            GlobalScreen.registerNativeHook();
            System.out.println("GlobalScreen registered successfully!");
            
            // Add key listener
            KeyCaptureTest listener = new KeyCaptureTest();
            GlobalScreen.addNativeKeyListener(listener);
            System.out.println("Key listener added successfully!");
            
            System.out.println("\nPress any key to test. Press ESC to exit.");
            System.out.println("If you don't see key events, check:");
            System.out.println("1. Accessibility permissions for IntelliJ/Terminal");
            System.out.println("2. No other instance is capturing keys");
            System.out.println("3. Try running with sudo if on macOS\n");
            
            // Keep the program running
            while (true) {
                Thread.sleep(100);
            }
        } catch (NativeHookException e) {
            System.err.println("Failed to register native hook: " + e.getMessage());
            System.err.println("Error code: " + e.getCode());
            e.printStackTrace();
            
            if (e.getMessage().contains("Failed to enable access")) {
                System.err.println("\nOn macOS: Grant accessibility permissions to your IDE/Terminal:");
                System.err.println("System Preferences > Security & Privacy > Privacy > Accessibility");
            }
        } catch (InterruptedException e) {
            System.out.println("Program interrupted");
        } finally {
            try {
                GlobalScreen.unregisterNativeHook();
                System.out.println("GlobalScreen unregistered");
            } catch (NativeHookException e) {
                System.err.println("Error unregistering hook: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        System.out.println("Key Pressed: " + NativeKeyEvent.getKeyText(e.getKeyCode()) + 
                         " (Code: " + e.getKeyCode() + ")");
        
        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            System.out.println("ESC pressed - Exiting...");
            System.exit(0);
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        System.out.println("Key Released: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used in this test
    }
}