import java.io.IOException;

public class ConsoleDisplay {
    private static final String CLEAR_SCREEN = "\033[H\033[2J";
    private static final String CURSOR_HOME = "\033[H";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private boolean useClearing = true;
    
    public ConsoleDisplay() {
        // Test if terminal supports ANSI escape codes
        testAnsiSupport();
        
        // Check for explicit disable
        String forceDisable = System.getProperty("console.noclear", "false");
        if ("true".equalsIgnoreCase(forceDisable)) {
            useClearing = false;
            System.out.println("[Console clearing disabled by system property]");
        } else if (useClearing) {
            System.out.println("[Console clearing enabled - terminal supports ANSI]");
        } else {
            System.out.println("[Console clearing disabled - terminal does not support ANSI]");
        }
    }
    
    private void testAnsiSupport() {
        // IntelliJ and most modern terminals support ANSI
        String term = System.getenv("TERM");
        String idePrefix = System.getProperty("idea.launcher.bin.path");
        
        // Check various indicators
        if (idePrefix != null) {
            // Running in IntelliJ IDEA
            useClearing = true;
        } else if (IS_WINDOWS && term == null) {
            // Windows without proper terminal
            useClearing = false;
        } else if (term != null && (term.contains("xterm") || term.contains("color"))) {
            // Modern terminal with color support
            useClearing = true;
        } else {
            // Default to false for safety
            useClearing = false;
        }
    }
    
    public void clear() {
        if (useClearing) {
            System.out.print(CLEAR_SCREEN);
            System.out.flush();
        } else {
            // Fallback: print many newlines
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }
    
    public void moveCursorHome() {
        if (useClearing) {
            System.out.print(CURSOR_HOME);
            System.out.flush();
        }
    }
    
    public void displayTranscriptBuffer(String content) {
        if (useClearing) {
            // Clear screen and display at top
            clear();
            System.out.print(content);
            System.out.flush();
        } else {
            // Just print with separator for non-ANSI terminals
            System.out.println("\n" + "=".repeat(80));
            System.out.print(content);
        }
    }
    
    public void appendToDisplay(String content) {
        System.out.print(content);
        System.out.flush();
    }
    
    public void setUseClearing(boolean useClearing) {
        this.useClearing = useClearing;
    }
}