import java.io.PrintStream;

/**
 * A dedicated standalone class to manage system instructions with proper thread safety
 * and singleton pattern to ensure consistent state across the application.
 */
public class InstructionManager {
    // The singleton instance of the instruction
    private static final InstructionManager INSTANCE = new InstructionManager();

    // The actual instruction value - marked as volatile for thread visibility
    private volatile String currentInstruction;

    // Private constructor to enforce singleton pattern
    private InstructionManager() {
        currentInstruction = "You are a software engineer and systems architect with 10 years experience. Answer as concisely as possible.";
    }

    /**
     * Get the current instruction value
     * @return The current system instruction
     */
    public static synchronized String get() {
        return INSTANCE.currentInstruction;
    }

    /**
     * Set a new instruction value
     * @param newInstruction The new system instruction
     * @param output PrintStream for logging
     * @return The updated instruction value
     */
    public static synchronized String set(String newInstruction, PrintStream output) {
        if (newInstruction != null && !newInstruction.isEmpty()) {
            String oldValue = INSTANCE.currentInstruction;
            INSTANCE.currentInstruction = newInstruction.trim();

            output.println("**************************************");
            output.println("SYSTEM INSTRUCTION CHANGED!");
            output.println("OLD: \"" + oldValue + "\"");
            output.println("NEW: \"" + INSTANCE.currentInstruction + "\"");
            output.println("**************************************");
        }

        return INSTANCE.currentInstruction;
    }
}
