//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import javax.sound.sampled.*;
import java.io.IOException;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.google.api.gax.rpc.BidiStream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Scanner;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.GlobalScreen;

public class Main {
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String CEREBRAS_API_KEY = System.getenv("CEREBRAS_API_KEY");

    // Adding a flag that can be used to signal program exit
    private static volatile boolean shouldExit = false;

    // Rate limiting configuration - minimum time between API requests in milliseconds
    private static final long RATE_LIMIT_MS = 1000; // 1 second between requests
    private static long lastRequestTime = 0;

    // Create separate output streams for regular and AI output
    private static PrintStream regularOutput = System.out;
    private static PrintStream aiOutput = System.out; // By default, use System.out
    private static final String AI_OUTPUT_FILE = "ai_output.log";

    // Flag to determine if AI output should be separated
    private static boolean separateAiOutput = Boolean.parseBoolean(System.getProperty("separate.ai.output", "false"));
    
    // VAD checker instance
    private static VADChecker vadChecker = new VADChecker();
    
    // Global hotkey listener
    private static GlobalHotkeyListener hotkeyListener;
    
    // Transcript buffer and navigation
    private static TranscriptBuffer transcriptBuffer = new TranscriptBuffer();
    private static TranscriptNavigationHandler navigationHandler;

    public static void main(String[] args) throws LineUnavailableException {
        // Initialize separate output stream if requested
        if (separateAiOutput) {
            try {
                // This will be shown in the regular console
                regularOutput.println("Initializing separate AI output stream...");

                // Keep regular output on System.out
                regularOutput = System.out;

                // Create a new output file that IntelliJ can attach to a different console
                aiOutput = new PrintStream(new FileOutputStream(AI_OUTPUT_FILE, true));
                aiOutput.println("AI output initialized");
            } catch (Exception e) {
                regularOutput.println("Failed to initialize separate AI output: " + e.getMessage());
                // Fall back to using System.out for AI output
                aiOutput = System.out;
            }
        }

        regularOutput.println("Hello and welcome!");

        for (int i = 1; i <= 5; i++) {
            regularOutput.println("i = " + i);
        }

        // List available mixers and their target lines
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        regularOutput.println("Available audio input devices:");

        List<Mixer.Info> inputDevices = new ArrayList<>();
        for(Mixer.Info mxr:mixers) {
            Mixer mixer = AudioSystem.getMixer(mxr);
            Line.Info[] targetLineInfo = mixer.getTargetLineInfo();
            if (targetLineInfo.length > 0) {
                inputDevices.add(mxr);
                regularOutput.println(inputDevices.size() - 1 + ": " + mxr.getName() + " - " + mxr.getDescription());
            }
        }

        // Prompt user to select an input device
        regularOutput.println("\nEnter the number of the input device to use:");
        Scanner deviceScanner = new Scanner(System.in);
        int deviceIndex = -1;
        while (deviceIndex < 0 || deviceIndex >= inputDevices.size()) {
            try {
                String input = deviceScanner.nextLine().trim();
                deviceIndex = Integer.parseInt(input);
                if (deviceIndex < 0 || deviceIndex >= inputDevices.size()) {
                    regularOutput.println("Invalid selection. Please enter a number between 0 and " + (inputDevices.size() - 1));
                }
            } catch (NumberFormatException e) {
                regularOutput.println("Please enter a valid number");
            }
        }

        Mixer.Info micInput = inputDevices.get(deviceIndex);
        regularOutput.println("Selected input device: " + micInput.getName());

        Mixer micMixer = AudioSystem.getMixer(micInput);
        // Get the first available TargetDataLine from the selected mixer
        Line.Info[] targetLineInfos = micMixer.getTargetLineInfo();
        TargetDataLine microphone = (TargetDataLine) micMixer.getLine(targetLineInfos[0]);
        // Audio format setup
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        microphone.open(format);

        try {
            // Initialize VAD checker
            regularOutput.println("Initializing Voice Activity Detector...");
            vadChecker.start();
            
            // Initialize global native hook system first
            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                regularOutput.println("Error: Could not register native keyboard hook: " + e.getMessage());
                return;
            }
            
            // Initialize key bindings
            KeyBindingConfig keyConfig = new KeyBindingConfig();
            keyConfig.loadFromFile();
            
            // Always prompt for configuration
            Scanner configScanner = new Scanner(System.in);
            regularOutput.println("\n" + "=".repeat(80));
            regularOutput.println("KEY BINDING CONFIGURATION");
            regularOutput.println("=".repeat(80));
            
            boolean hasExistingBindings = keyConfig.getBinding(KeyBindingConfig.Action.SUBMIT) != null;
            if (hasExistingBindings) {
                regularOutput.println("Existing key bindings found.");
                regularOutput.print("Reconfigure key bindings? (Y/N): ");
            } else {
                regularOutput.println("No key bindings found. Configuration is required.");
                regularOutput.print("Configure key bindings now? (Y/N): ");
            }
            
            String response = configScanner.nextLine().trim().toUpperCase();
            
            if (response.equals("Y") || response.equals("YES") || !hasExistingBindings) {
                if (!hasExistingBindings && !response.equals("Y") && !response.equals("YES")) {
                    regularOutput.println("\nKey binding configuration is mandatory for first-time use.");
                }
                try {
                    KeyBindingSetup setup = new KeyBindingSetup(keyConfig);
                    setup.setupBindings();
                } catch (Exception e) {
                    regularOutput.println("Error setting up key bindings: " + e.getMessage());
                    regularOutput.println("Cannot continue without key bindings. Exiting.");
                    return;
                }
            } else {
                regularOutput.println("Using existing key bindings.");
            }
            
            // Set config on transcript buffer for display
            transcriptBuffer.setKeyConfig(keyConfig);
            
            // Initialize global hotkey listener
            try {
                hotkeyListener = new GlobalHotkeyListener() {
                    @Override
                    protected void onHotkeyPressed() {
                        // Custom action when hotkey is pressed
                        regularOutput.println("\n[HOTKEY] Alt+Ctrl+Shift+W pressed!");
                        // You can add more functionality here later
                    }
                };
                hotkeyListener.register();
                
                // Initialize transcript navigation handler with config
                navigationHandler = new TranscriptNavigationHandler(transcriptBuffer, Main::processApiRequest, keyConfig);
                navigationHandler.register();
            } catch (Exception e) {
                regularOutput.println("Warning: Could not register global hotkey listeners: " + e.getMessage());
                // Continue without hotkey support
            }
            // Google Speech-to-Text streaming setup
            // Set up credentials with quota project ID
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setQuotaProjectId("bettnet-sporting")
                    .build();
            SpeechClient speechClient = SpeechClient.create(settings);
            try {
                RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setLanguageCode("en-US")
                        .setSampleRateHertz(16000)
                        .build();
                StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                        .setConfig(recConfig)
                        .setInterimResults(true)
                        .build();
                
                regularOutput.println("Capturing audio and streaming to Google Speech-to-Text...");
                byte[] buffer = new byte[4096];
                microphone.start();
                
                // Shared state for response handling
                final Object responseLock = new Object();
                final boolean[] hasInterimResult = {false};
                final Thread[] currentResponseThread = {null};
                final BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse>[] currentStream = new BidiStream[]{null};
                
                // Response handler that can work with multiple streams
                Runnable responseHandler = () -> {
                    BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> myStream;
                    synchronized (responseLock) {
                        myStream = currentStream[0];
                    }
                    
                    try {
                        Iterator<StreamingRecognizeResponse> responseIterator = myStream.iterator();
                        while (responseIterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                            StreamingRecognizeResponse nextResponse = responseIterator.next();

                            for (StreamingRecognitionResult result : nextResponse.getResultsList()) {
                                String transcript = result.getAlternatives(0).getTranscript();
                                boolean isFinal = result.getIsFinal();

                                if (isFinal) {
                                    // Clear any interim display
                                    synchronized (responseLock) {
                                        if (hasInterimResult[0]) {
                                            regularOutput.print("\r" + " ".repeat(80) + "\r");
                                            hasInterimResult[0] = false;
                                        }
                                    }

                                    // Print final results with clear formatting
                                    String cleanedTranscript = transcript.trim();
                                    if (!cleanedTranscript.isEmpty()) {
                                        // Add to transcript buffer
                                        transcriptBuffer.addTranscript(cleanedTranscript);
                                        
                                        // Output to both regular and AI outputs with better formatting
                                        String formattedOutput = "USER: " + cleanedTranscript;
                                        regularOutput.println(formattedOutput);

                                        // Only write to AI output if it's different from regularOutput
                                        if (aiOutput != regularOutput) {
                                            aiOutput.println(formattedOutput);
                                        }
                                        
                                        // No longer auto-submitting questions - only submit via CMD+ALT+W
                                    }
                                }
                                else {
                                    // Show interim results - with better formatting
                                    synchronized (responseLock) {
                                        hasInterimResult[0] = true;
                                    }
                                    String interimText = "\rInterim: " + transcript;
                                    regularOutput.print(interimText);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            regularOutput.println("\nResponse thread error: " + e.getMessage());
                        }
                    }
                };

                // Add a keyboard input handler thread to watch for command keys
                Thread keyboardThread = new Thread(() -> {
                    Scanner scanner = new Scanner(System.in);
                    regularOutput.println("Press 'i' to update system instructions, or 'q' to quit");

                    while (!shouldExit) {
                        try {
                            // Check if there is input available
                            if (System.in.available() > 0 || scanner.hasNextLine()) {
                                // Wait for input
                                String input = scanner.nextLine().trim();

                                // Process the command
                                if (input.equalsIgnoreCase("i")) {
                                    // Prompt for new system instruction
                                    regularOutput.println("\nEnter new system instruction (press Enter when done):");

                                    // Make sure we can read the next line
                                    String newInstruction = scanner.nextLine();

                                    if (!newInstruction.trim().isEmpty()) {
                                        // Update the instruction
                                        String verifiedInstruction = InstructionManager.set(newInstruction.trim(), regularOutput);

                                        // Verify the update worked by retrieving the current value
                                        regularOutput.println("VERIFICATION - Current system instruction: \"" + InstructionManager.get() + "\"");

                                        if (aiOutput != regularOutput) {
                                            aiOutput.println("System instruction updated: \"" + verifiedInstruction + "\"");
                                        }
                                    } else {
                                        regularOutput.println("Instruction unchanged (empty input)");
                                    }

                                    regularOutput.println("\nPress 'i' to update system instructions, or 'q' to quit");
                                } else if (input.equalsIgnoreCase("q")) {
                                    regularOutput.println("Exiting...");
                                    shouldExit = true;
                                    System.exit(0);
                                }
                            } else {
                                // Short sleep to prevent CPU hogging
                                Thread.sleep(100);
                            }
                        } catch (Exception e) {
                            regularOutput.println("Error reading keyboard input: " + e.getMessage());
                            e.printStackTrace();
                            // Reset the scanner after an error
                            scanner = new Scanner(System.in);
                        }
                    }
                });
                keyboardThread.setDaemon(true);
                keyboardThread.start();

                // Initial stream creation
                synchronized (responseLock) {
                    currentStream[0] = speechClient.streamingRecognizeCallable().call();
                    currentStream[0].send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build());
                    currentResponseThread[0] = new Thread(responseHandler);
                    currentResponseThread[0].setDaemon(true);
                    currentResponseThread[0].start();
                }
                
                // Audio capture loop with VAD
                byte[] vadBuffer = new byte[320]; // 20ms chunks for VAD
                boolean inSpeech = false;
                int silenceCount = 0;
                long streamStartTime = System.currentTimeMillis();
                long lastKeepAlive = System.currentTimeMillis();
                
                while (!shouldExit) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        // Check VAD for the first chunk to detect speech start/end
                        boolean currentlySpeaking = false;
                        if (bytesRead >= 320) {
                            System.arraycopy(buffer, 0, vadBuffer, 0, 320);
                            try {
                                currentlySpeaking = vadChecker.isSpeech(vadBuffer);
                            } catch (IOException e) {
                                regularOutput.println("VAD error: " + e.getMessage());
                                currentlySpeaking = true; // Default to sending audio on VAD error
                            }
                        }
                        
                        // Update speech state
                        if (currentlySpeaking) {
                            if (!inSpeech) {
//                                regularOutput.println("\n[Speech detected]");
                            }
                            inSpeech = true;
                            silenceCount = 0;
                        } else if (inSpeech) {
                            silenceCount++;
                            // Stop sending after 500ms of silence (25 chunks * 20ms)
                            if (silenceCount > 25) {
                                inSpeech = false;
//                                regularOutput.println("\n[Speech ended]");
                            }
                        }
                        
                        // Check if we need to reconnect (50 seconds limit to be safe)
                        if (System.currentTimeMillis() - streamStartTime > 50000) {
                            regularOutput.println("\n[Reconnecting stream to avoid timeout...]");
                            
                            synchronized (responseLock) {
                                // Close old stream
                                if (currentStream[0] != null) {
                                    try {
                                        currentStream[0].closeSend();
                                    } catch (Exception e) {
                                        // Ignore errors on close
                                    }
                                }
                                
                                // Interrupt old response thread
                                if (currentResponseThread[0] != null) {
                                    currentResponseThread[0].interrupt();
                                    try {
                                        currentResponseThread[0].join(1000);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                
                                // Create new stream
                                try {
                                    currentStream[0] = speechClient.streamingRecognizeCallable().call();
                                    currentStream[0].send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build());
                                    currentResponseThread[0] = new Thread(responseHandler);
                                    currentResponseThread[0].setDaemon(true);
                                    currentResponseThread[0].start();
                                    streamStartTime = System.currentTimeMillis();
                                    regularOutput.println("[Stream reconnected successfully]");
                                } catch (Exception e) {
                                    regularOutput.println("Failed to reconnect stream: " + e.getMessage());
                                    shouldExit = true;
                                    break;
                                }
                            }
                        }
                        
                        // Send audio when in speech mode
                        if (inSpeech || currentlySpeaking) {
                            synchronized (responseLock) {
                                if (currentStream[0] != null) {
                                    try {
                                        currentStream[0].send(StreamingRecognizeRequest.newBuilder()
                                                .setAudioContent(ByteString.copyFrom(buffer, 0, bytesRead))
                                                .build());
                                        lastKeepAlive = System.currentTimeMillis();
                                    } catch (Exception e) {
                                        regularOutput.println("\nError sending audio: " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            // Send minimal keep-alive audio every 5 seconds during silence
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastKeepAlive > 5000) {
                                synchronized (responseLock) {
                                    if (currentStream[0] != null) {
                                        try {
                                            // Send a tiny amount of silence to keep connection alive
                                            byte[] silence = new byte[320];
                                            currentStream[0].send(StreamingRecognizeRequest.newBuilder()
                                                    .setAudioContent(ByteString.copyFrom(silence))
                                                    .build());
                                            lastKeepAlive = currentTime;
                                        } catch (Exception e) {
                                            // Ignore keep-alive errors
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Clean up streaming resources
                synchronized (responseLock) {
                    if (currentStream[0] != null) {
                        try {
                            currentStream[0].closeSend();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    if (currentResponseThread[0] != null) {
                        currentResponseThread[0].interrupt();
                    }
                }
                
            } finally {
                // Close speech client
                if (speechClient != null) {
                    speechClient.close();
                }
            }
        } catch (Exception e) {
            regularOutput.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (microphone != null && microphone.isOpen()) {
                microphone.close();
            }
            
            // Stop VAD checker
            try {
                vadChecker.stop();
                regularOutput.println("VAD checker stopped.");
            } catch (Exception e) {
                regularOutput.println("Error stopping VAD: " + e.getMessage());
            }
            
            // Unregister hotkey listeners
            if (hotkeyListener != null) {
                try {
                    hotkeyListener.unregister();
                    regularOutput.println("Hotkey listener unregistered.");
                } catch (Exception e) {
                    regularOutput.println("Error unregistering hotkey listener: " + e.getMessage());
                }
            }
            
            if (navigationHandler != null) {
                navigationHandler.unregister();
                regularOutput.println("Navigation handler unregistered.");
            }
            
            // Unregister GlobalScreen
            try {
                GlobalScreen.unregisterNativeHook();
                regularOutput.println("GlobalScreen unregistered.");
            } catch (Exception e) {
                regularOutput.println("Error unregistering GlobalScreen: " + e.getMessage());
            }

            // Close output streams if they were separately created
            if (separateAiOutput && aiOutput != System.out) {
                aiOutput.close();
            }
        }
    }

    private static void processApiRequest(String question) {
        // Implement proper rate limiting
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < RATE_LIMIT_MS) {
            try {
                Thread.sleep(RATE_LIMIT_MS - (currentTime - lastRequestTime));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                regularOutput.println("Rate limiting interrupted: " + e.getMessage());
            }
        }
        lastRequestTime = System.currentTimeMillis();

        // Log that we're making an API request
        regularOutput.println("Processing request: \"" + question + "\"");
        if (aiOutput != regularOutput) {
            aiOutput.println("Processing request: \"" + question + "\"");
        }

        try {
            String apiResponse;
            //openAi is disabled because it is too slow. You might have a usecase that does not care about latency.
//            if (OPENAI_API_KEY != null && !OPENAI_API_KEY.isEmpty()) {
//                apiResponse = callOpenAI(question);
//            } else
            if (CEREBRAS_API_KEY != null && !CEREBRAS_API_KEY.isEmpty()) {
                apiResponse = callCerebras(question);
            } else {
                regularOutput.println("ERROR: No API keys available for OpenAI or Cerebras");
                if (aiOutput != regularOutput) {
                    aiOutput.println("ERROR: No API keys available for OpenAI or Cerebras");
                }
                return;
            }

            // Format and display the response on both outputs
            regularOutput.println("AI: " + apiResponse);
            if (aiOutput != regularOutput) {
                aiOutput.println("AI: " + apiResponse);
            }
        } catch (Exception e) {
            String errorMsg = "Error processing API request: " + e.getMessage();
            regularOutput.println(errorMsg);
            if (aiOutput != regularOutput) {
                aiOutput.println(errorMsg);
            }
            e.printStackTrace();
        }
    }

    private static String callOpenAI(String question) throws IOException {
        // Basic implementation of OpenAI API call
        URL url = new URL("https://api.openai.com/v1/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        connection.setDoOutput(true);

        // Create the request body
        String jsonInputString = String.format(
            "{\"model\": \"gpt-4-turbo\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}]}",
            InstructionManager.get().replace("\"", "\\\""),
            question.replace("\"", "\\\"")
        );

        // Send the request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read the response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        // Parse the JSON response to extract the assistant's message
        // Using simple string operations for demo purposes
        // In production, use a proper JSON parser
        String jsonResponse = response.toString();
        int contentStart = jsonResponse.indexOf("\"content\":\"") + 11;
        int contentEnd = jsonResponse.indexOf("\"", contentStart);
        String responseSubstr = jsonResponse.substring(contentStart, contentEnd)
               .replace("\\n", "\n")
               .replace("\\\"", "\"")
               .trim();
        // Collapse multiple newlines into single newlines
        return responseSubstr.replaceAll("\n\n+", "\n");
    }

    private static String callCerebras(String question) throws IOException {
        // Flag to control whether to actually make API requests or just simulate them
        final boolean ENABLE_API_REQUESTS = true;

        // Force refresh the current instruction by explicitly requesting it
        String currentInstruction = InstructionManager.get();

        // Extra debugging to confirm we're using the correct instruction (disabled)
        // regularOutput.println("FINAL VERIFICATION - Using instruction: \"" + currentInstruction + "\"");

        // Create the request body for Cerebras API format
        String jsonInputString = String.format(
            "{\"model\": \"llama-4-scout-17b-16e-instruct\", " +
            "\"stream\": false, " +
            "\"messages\": [" +
            "{\"content\": \"%s\", \"role\": \"system\"}, " +
            "{\"content\": \"%s\", \"role\": \"user\"}" +
            "], " +
            "\"temperature\": 0, " +
            "\"max_tokens\": -1, " +
            "\"seed\": 0, " +
            "\"top_p\": 1}",
            currentInstruction.replace("\"", "\\\""),
            question.replace("\"", "\\\"")
        );

        // Debug: Log the actual instruction and request being used (disabled)
        // regularOutput.println("DEBUG - Sending Cerebras API request with system instruction: \"" + currentInstruction + "\"");
        // regularOutput.println("DEBUG - Full request body: " + jsonInputString);

        // Check if we should actually make the API request
        if (ENABLE_API_REQUESTS) {
            // Only set up the connection and make the actual API call if enabled
            URL url = new URL("https://api.cerebras.ai/v1/chat/completions");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + CEREBRAS_API_KEY);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds timeout
            connection.setReadTimeout(30000);    // 30 seconds read timeout

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check if the request was successful
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(
                        connection.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();
                throw new IOException("Cerebras API error: " + responseCode + " - " + errorResponse.toString());
            }

            // Read the successful response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // Parse the JSON response to extract the completion
            String jsonResponse = response.toString();
            int contentStart = jsonResponse.indexOf("\"content\":\"") + 11;
            int contentEnd = jsonResponse.indexOf("\"", contentStart);
            if (contentStart > 7 && contentEnd > contentStart) {
                String responseSubstr = jsonResponse.substring(contentStart, contentEnd)
                       .replace("\\n", "\n")
                       .replace("\\\"", "\"")
                       .trim();
                // Collapse multiple newlines into single newlines
                return responseSubstr.replaceAll("\n\n+", "\n");
            } else {
                // Fallback for parsing errors
                regularOutput.println("Warning: Failed to parse Cerebras API response. Raw response: " + jsonResponse);
                return "I received a response but couldn't parse it correctly.";
            }
        } else {
            // API requests are disabled - return a mock response instead
            regularOutput.println("REQUESTS DISABLED. Skipping sending request to Cerebras API.");
            return "API requests are disabled. This is a mock response. Your request was: \"" +
                   question + "\" with system instruction: \"" + currentInstruction + "\"";
        }
    }
}

