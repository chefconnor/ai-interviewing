import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VADChecker {
    private Process vadProcess;
    private OutputStream stdin;
    private BufferedReader stdout;
    private BufferedReader stderr;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final String VAD_SCRIPT_PATH = "/Users/c/IdeaProjects/untitled1/python-vad-detector/vad_check.py";

    public void start() throws IOException {
        // Check if Python script exists
        File scriptFile = new File(VAD_SCRIPT_PATH);
        if (!scriptFile.exists()) {
            throw new IOException("VAD script not found at: " + VAD_SCRIPT_PATH);
        }
        
        ProcessBuilder builder = new ProcessBuilder("python3", VAD_SCRIPT_PATH);
        builder.redirectErrorStream(false);
        
        System.out.println("Starting VAD process: " + String.join(" ", builder.command()));
        
        vadProcess = builder.start();
        stdin = vadProcess.getOutputStream();
        stdout = new BufferedReader(new InputStreamReader(vadProcess.getInputStream()));
        stderr = new BufferedReader(new InputStreamReader(vadProcess.getErrorStream()));
        
        // Start error reader thread
        Thread errorReader = new Thread(() -> {
            try {
                String line;
                while ((line = stderr.readLine()) != null) {
                    System.err.println("VAD Python: " + line);
                }
            } catch (IOException e) {
                System.err.println("VAD error reader terminated");
            }
        });
        errorReader.setDaemon(true);
        errorReader.start();
        
        // Wait a bit to see if process starts successfully
        try {
            Thread.sleep(100);
            if (!vadProcess.isAlive()) {
                throw new IOException("VAD process failed to start. Exit code: " + vadProcess.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        isRunning.set(true);
        System.out.println("VAD checker started successfully");
    }

    public boolean isSpeech(byte[] audioChunk) throws IOException {
        if (!isRunning.get()) {
            throw new IOException("VAD checker is not running");
        }
        
        if (audioChunk.length != 320) {
            throw new IllegalArgumentException("Audio chunk must be exactly 320 bytes, got " + audioChunk.length);
        }
        
        try {
            stdin.write(audioChunk);
            stdin.flush();
            String response = stdout.readLine();
            if (response == null) {
                isRunning.set(false);
                throw new IOException("VAD process terminated unexpectedly");
            }
            return "1".equals(response.trim());
        } catch (IOException e) {
            isRunning.set(false);
            throw new IOException("VAD communication error: " + e.getMessage(), e);
        }
    }

    public void stop() throws IOException, InterruptedException {
        isRunning.set(false);
        if (stdin != null) {
            stdin.close();
        }
        if (vadProcess != null) {
            vadProcess.waitFor(1, TimeUnit.SECONDS);
            if (vadProcess.isAlive()) {
                vadProcess.destroyForcibly();
            }
        }
    }
}