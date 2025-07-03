//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.google.api.gax.rpc.BidiStream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// A dedicated class to manage the instruction in a thread-safe way
class InstructionManager {
    // Using AtomicReference for thread safety
    private static final AtomicReference<String> instruction =
            new AtomicReference<>("You are a helpful assistant.");

    // Get the current instruction
    public static String get() {
        return instruction.get();
    }

    // Update the instruction and return the new value
    public static String set(String newInstruction, PrintStream output) {
        if (newInstruction != null && !newInstruction.isEmpty()) {
            String oldValue = instruction.getAndSet(newInstruction.trim());
            output.println("**************************************");
            output.println("SYSTEM INSTRUCTION CHANGED!");
            output.println("OLD: \"" + oldValue + "\"");
            output.println("NEW: \"" + instruction.get() + "\"");
            output.println("**************************************");
            return instruction.get();
        }
        return instruction.get();
    }
}

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

    public static void main(String[] args) throws LineUnavailableException, IOException {
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

        // Filter to only include input devices
        List<Mixer.Info> inputDevices = new ArrayList<>();
        for (int i = 0; i < mixers.length; i++) {
            Mixer mixer = AudioSystem.getMixer(mixers[i]);
            Line.Info[] targetLineInfo = mixer.getTargetLineInfo();
            if (targetLineInfo.length > 0) {
                inputDevices.add(mixers[i]);
                regularOutput.println(inputDevices.size() - 1 + ": " + mixers[i].getName() + " - " + mixers[i].getDescription());
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
            // Google Speech-to-Text streaming setup
            // Set up credentials with quota project ID
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setQuotaProjectId("bettnet-sporting")
                    .build();
            try (SpeechClient speechClient = SpeechClient.create(settings)) {
                RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setLanguageCode("en-US")
                        .setSampleRateHertz(16000)
                        .build();
                StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                        .setConfig(recConfig)
                        .setInterimResults(true)
                        .build();
                BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream =
                        speechClient.streamingRecognizeCallable().call();
                // Send the initial configuration message
                stream.send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build());
                regularOutput.println("Capturing audio and streaming to Google Speech-to-Text...");
                byte[] buffer = new byte[4096];
                microphone.start();

                // Create a thread for reading responses to avoid blocking the audio capture
                Thread responseThread = new Thread(() -> {
                    try {
                        StringBuilder interimTranscript = new StringBuilder();
                        Iterator<StreamingRecognizeResponse> responseIterator = stream.iterator();
                        while (responseIterator.hasNext()) {
                            StreamingRecognizeResponse response = responseIterator.next();

                            // Handle interim results differently from final results
                            boolean hasInterimResult = false;

                            for (StreamingRecognitionResult result : response.getResultsList()) {
                                String transcript = result.getAlternatives(0).getTranscript();
                                boolean isFinal = result.getIsFinal();

                                if (isFinal) {
                                    // Clear any interim display
                                    if (hasInterimResult) {
                                        regularOutput.print("\r" + " ".repeat(80) + "\r");
                                    }

                                    // Print final results with clear formatting
                                    String cleanedTranscript = transcript.trim();
                                    if (!cleanedTranscript.isEmpty()) {
                                        // Output to both regular and AI outputs with better formatting
                                        String formattedOutput = "USER: " + cleanedTranscript;
                                        regularOutput.println(formattedOutput);

                                        // Only write to AI output if it's different from regularOutput
                                        if (aiOutput != regularOutput) {
                                            aiOutput.println(formattedOutput);
                                        }

                                        // Process with OpenAI if it appears to be a question
                                        if (cleanedTranscript.contains("?") ||
                                            cleanedTranscript.toLowerCase().startsWith("how") ||
                                            cleanedTranscript.toLowerCase().startsWith("what") ||
                                            cleanedTranscript.toLowerCase().startsWith("why") ||
                                            cleanedTranscript.toLowerCase().startsWith("when") ||
                                            cleanedTranscript.toLowerCase().startsWith("who") ||
                                            cleanedTranscript.toLowerCase().startsWith("where") ||
                                            cleanedTranscript.toLowerCase().startsWith("can") ||
                                            cleanedTranscript.toLowerCase().startsWith("could") ||
                                            cleanedTranscript.toLowerCase().startsWith("would") ||
                                            cleanedTranscript.toLowerCase().startsWith("should")) {

                                            // Make API call to OpenAI or Cerebras
                                            processApiRequest(cleanedTranscript);
                                        }
                                    }
                                }
                                else {
                                    // Show interim results - with better formatting
                                    hasInterimResult = true;
                                    String interimText = "\rInterim: " + transcript;
                                    regularOutput.print(interimText);
                                }
                            }
                        }
                    } catch (Exception e) {
                        regularOutput.println("Error in response thread: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                responseThread.setDaemon(true);
                responseThread.start();

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
                                        regularOutput.println("Verification - Current system instruction: \"" + verifiedInstruction + "\"");

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

                // Audio capture loop
                while (!shouldExit) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        stream.send(StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(buffer, 0, bytesRead))
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            regularOutput.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (microphone != null && microphone.isOpen()) {
                microphone.close();
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
            // Try OpenAI first, fall back to Cerebras if needed
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
            regularOutput.println("\nAI: " + apiResponse + "\n");
            if (aiOutput != regularOutput) {
                aiOutput.println("\nAI: " + apiResponse + "\n");
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
        return jsonResponse.substring(contentStart, contentEnd)
               .replace("\\n", "\n")
               .replace("\\\"", "\"");
    }

    private static String callCerebras(String question) throws IOException {
        // Flag to control whether to actually make API requests or just simulate them
        final boolean ENABLE_API_REQUESTS = false;

        // Get the current instruction directly from the InstructionManager
        String currentInstruction = InstructionManager.get();

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

        // Debug: Log the actual instruction and request being used
        regularOutput.println("INSTRUCTION CHECK - Current system instruction: \"" + currentInstruction + "\"");
        regularOutput.println("DEBUG - Sending Cerebras API request with system instruction: \"" + currentInstruction + "\"");
        regularOutput.println("DEBUG - Full request body: " + jsonInputString);

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
                return jsonResponse.substring(contentStart, contentEnd)
                       .replace("\\n", "\n")
                       .replace("\\\"", "\"")
                       .trim();
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
