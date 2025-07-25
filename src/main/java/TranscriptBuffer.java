import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TranscriptBuffer {
    private static final String BUFFER_FILE = "transcript_buffer.txt";
    private static final int DISPLAY_CONTEXT_LINES = 2; // Show 2 lines above and below selection
    
    private final List<String> transcripts = new ArrayList<>();
    private int selectionIndex = 0;
    private int slurpStartIndex = -1; // -1 means no slurping active
    private boolean continuousMode = false; // For continuous collection mode
    private int continuousModeStartIndex = -1;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService persistenceExecutor = Executors.newSingleThreadScheduledExecutor();
    
    private volatile boolean isDirty = false;
    private KeyBindingConfig keyConfig;
    private final ConsoleDisplay console = new ConsoleDisplay();
    
    public TranscriptBuffer() {
        loadFromDisk();
        
        // Schedule periodic persistence every 2 minutes
        persistenceExecutor.scheduleAtFixedRate(() -> {
            if (isDirty) {
                saveToDisk();
            }
        }, 2, 2, TimeUnit.MINUTES);
        
        // Add shutdown hook for final save
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    public void addTranscript(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            transcripts.add(transcript.trim());
            // Move selection to the latest transcript
            selectionIndex = transcripts.size() - 1;
            
            // Don't reset slurp if in continuous mode
            if (!continuousMode) {
                resetSlurp();
            }
            
            isDirty = true;
            displayBuffer();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void moveUp() {
        lock.writeLock().lock();
        try {
            if (selectionIndex > 0) {
                selectionIndex--;
                resetSlurp();
                displayBuffer();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void moveDown() {
        lock.writeLock().lock();
        try {
            if (selectionIndex < transcripts.size() - 1) {
                selectionIndex++;
                resetSlurp();
                displayBuffer();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void slurpPrevious() {
        lock.writeLock().lock();
        try {
            if (transcripts.isEmpty()) {
                return;
            }
            
            // Initialize slurp if not active
            if (slurpStartIndex == -1) {
                slurpStartIndex = selectionIndex;
            }
            
            // Extend slurp backwards if possible
            if (slurpStartIndex > 0) {
                slurpStartIndex--;
            }
            
            displayBuffer();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public String getSelection() {
        lock.readLock().lock();
        try {
            if (transcripts.isEmpty()) {
                return "";
            }
            
            int startIdx = selectionIndex;
            int endIdx = selectionIndex;
            
            // Determine range based on mode
            if (continuousMode && continuousModeStartIndex != -1) {
                // In continuous mode, include everything from start to current
                startIdx = continuousModeStartIndex;
                endIdx = selectionIndex;
            } else if (slurpStartIndex != -1 && slurpStartIndex <= selectionIndex) {
                // In slurp mode
                startIdx = slurpStartIndex;
                endIdx = selectionIndex;
            }
            
            // Build the selection
            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i <= endIdx; i++) {
                if (i > startIdx) {
                    sb.append(" ");
                }
                sb.append(transcripts.get(i));
            }
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void toggleContinuousMode() {
        lock.writeLock().lock();
        try {
            continuousMode = !continuousMode;
            if (continuousMode) {
                // Starting continuous mode - mark current position
                continuousModeStartIndex = selectionIndex;
                System.out.println("\n[CONTINUOUS MODE STARTED] - All new transcripts will be collected");
            } else {
                // Ending continuous mode
                continuousModeStartIndex = -1;
                System.out.println("\n[CONTINUOUS MODE ENDED]");
            }
            displayBuffer();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void clearSelection() {
        lock.writeLock().lock();
        try {
            slurpStartIndex = -1;
            if (continuousMode) {
                continuousMode = false;
                continuousModeStartIndex = -1;
                System.out.println("\n[SELECTION CLEARED - Continuous mode ended]");
            } else {
                System.out.println("\n[SELECTION CLEARED]");
            }
            displayBuffer();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void setKeyConfig(KeyBindingConfig config) {
        this.keyConfig = config;
    }
    
    private void resetSlurp() {
        slurpStartIndex = -1;
    }
    
    private void displayBuffer() {
        StringBuilder display = new StringBuilder();
        display.append("=".repeat(80)).append("\n");
        display.append("TRANSCRIPT BUFFER\n");
        display.append("=".repeat(80)).append("\n");
        
        if (transcripts.isEmpty()) {
            display.append("[No transcripts yet]\n");
            console.displayTranscriptBuffer(display.toString());
            return;
        }
        
        // Calculate display range
        int startIdx = Math.max(0, selectionIndex - DISPLAY_CONTEXT_LINES);
        int endIdx = Math.min(transcripts.size() - 1, selectionIndex + DISPLAY_CONTEXT_LINES);
        
        // Ensure we show at least 5 lines if available
        int totalLines = endIdx - startIdx + 1;
        if (totalLines < 5 && transcripts.size() >= 5) {
            if (startIdx == 0) {
                endIdx = Math.min(4, transcripts.size() - 1);
            } else if (endIdx == transcripts.size() - 1) {
                startIdx = Math.max(0, transcripts.size() - 5);
            }
        }
        
        // Display transcripts
        for (int i = startIdx; i <= endIdx; i++) {
            String prefix = (i == selectionIndex) ? "--> " : "    ";
            String suffix = "";
            
            // Mark slurped range
            if (slurpStartIndex != -1 && i >= slurpStartIndex && i <= selectionIndex) {
                suffix = " [SLURPED]";
            }
            
            display.append(String.format("[%3d] %s%s%s%n", i, prefix, transcripts.get(i), suffix));
        }
        
        // Show navigation info
        display.append("\n").append("-".repeat(80)).append("\n");
        if (keyConfig != null) {
            display.append(String.format("Navigation: %s/%s = Move | %s = Slurp | %s = Submit | %s = Continuous | %s = Clear%n",
                keyConfig.getDisplayString(KeyBindingConfig.Action.MOVE_UP),
                keyConfig.getDisplayString(KeyBindingConfig.Action.MOVE_DOWN),
                keyConfig.getDisplayString(KeyBindingConfig.Action.SLURP_PREVIOUS),
                keyConfig.getDisplayString(KeyBindingConfig.Action.SUBMIT),
                keyConfig.getDisplayString(KeyBindingConfig.Action.CONTINUOUS_MODE),
                keyConfig.getDisplayString(KeyBindingConfig.Action.CLEAR_SELECTION)));
            display.append(String.format("Audio: %s = Mute (hold to pause capture)%n",
                keyConfig.getDisplayString(KeyBindingConfig.Action.MUTE_WHILE_HELD)));
        } else {
            display.append("Navigation: Key bindings not configured\n");
        }
        display.append(String.format("Position: %d of %d", selectionIndex + 1, transcripts.size()));
        if (continuousMode) {
            display.append(" [CONTINUOUS MODE ACTIVE]");
        }
        display.append("\n");
        
        // Display active selection if any
        if ((slurpStartIndex != -1 && slurpStartIndex <= selectionIndex) || 
            (continuousMode && continuousModeStartIndex != -1)) {
            display.append("\n").append("~".repeat(80)).append("\n");
            if (continuousMode) {
                display.append("CONTINUOUS MODE SELECTION (will be submitted with ");
                display.append(keyConfig.getDisplayString(KeyBindingConfig.Action.SUBMIT)).append("):\n");
            } else {
                display.append("SLURPED SELECTION (will be submitted with ");
                display.append(keyConfig.getDisplayString(KeyBindingConfig.Action.SUBMIT)).append("):\n");
            }
            display.append("~".repeat(80)).append("\n");
            display.append(getSelection()).append("\n");
            display.append("~".repeat(80)).append("\n");
        }
        
        // Display everything at once
        console.displayTranscriptBuffer(display.toString());
    }
    
    private void loadFromDisk() {
        Path path = Paths.get(BUFFER_FILE);
        if (!Files.exists(path)) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            transcripts.clear();
            transcripts.addAll(lines);
            
            // Set selection to last item
            if (!transcripts.isEmpty()) {
                selectionIndex = transcripts.size() - 1;
            }
            
            System.out.println("Loaded " + transcripts.size() + " transcripts from disk");
        } catch (IOException e) {
            System.err.println("Error loading transcript buffer: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void saveToDisk() {
        lock.readLock().lock();
        try {
            Path path = Paths.get(BUFFER_FILE);
            Files.write(path, transcripts, StandardCharsets.UTF_8);
            isDirty = false;
            // No longer creating timestamped backups
        } catch (IOException e) {
            System.err.println("Error saving transcript buffer: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void shutdown() {
        persistenceExecutor.shutdown();
        if (isDirty) {
            saveToDisk();
        }
        
        // Delete all transcript backup files
        try {
            Files.list(Paths.get("."))
                .filter(path -> path.getFileName().toString().matches("transcript_backup_\\d{8}_\\d{6}\\.txt"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        System.out.println("Deleted backup: " + path.getFileName());
                    } catch (IOException e) {
                        System.err.println("Failed to delete backup: " + path.getFileName());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error cleaning up backup files: " + e.getMessage());
        }
    }
}