package com.example.geminivrcore;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log; // Added for logging
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity; // Activity should be explicitly imported

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures; // For Java Futures
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
// import com.google.ai.client.generativeai.type.鋰Part; // Corrected import if Part is needed, or use helper
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity"; // For logging

    private TextView textView;
    private Button speakButton;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech textToSpeech;

    // --- Gemini API Integration ---
    // IMPORTANT: Replace with your actual API key
    private static final String GEMINI_API_KEY = "YOUR_API_KEY";
    private GenerativeModelFutures generativeModelFutures;
    private ExecutorService executorService;
    // --- End Gemini API Integration ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        speakButton = findViewById(R.id.speakButton);

        // Initialize ExecutorService
        executorService = Executors.newSingleThreadExecutor();

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: Language not supported or data missing.");
                    textView.setText("TTS: Language not supported or data missing.");
                } else {
                    Log.i(TAG, "TTS initialized successfully.");
                }
            } else {
                Log.e(TAG, "TTS: Initialization failed. Status: " + status);
                textView.setText("TTS: Initialization failed.");
            }
        });

        // Initialize SpeechRecognizer
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available on this device.");
            textView.setText("Speech recognition not available.");
            speakButton.setEnabled(false);
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "onReadyForSpeech");
                    textView.setText("Listening...");
                }

                @Override
                public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech"); }
                @Override
                public void onRmsChanged(float rmsdB) { }
                @Override
                public void onBufferReceived(byte[] buffer) { }
                @Override
                public void onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech"); }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorText(error);
                    Log.e(TAG, "Speech Recognizer Error: " + error + " - " + errorMessage);
                    textView.setText("Error: " + errorMessage);
                    // speak("Speech Error: " + errorMessage); // Avoid speaking during potential audio system issues
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        Log.i(TAG, "Recognized text: " + recognizedText);
                        textView.setText("You said: " + recognizedText);
                        // Send recognizedText to Gemini API
                        callGeminiApi(recognizedText);
                    } else {
                        Log.w(TAG, "No speech recognition results found.");
                        textView.setText("Could not understand. Try again.");
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) { }
                @Override
                public void onEvent(int eventType, Bundle params) { }
            });

            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            speakButton.setOnClickListener(v -> {
                if (GEMINI_API_KEY.equals("YOUR_API_KEY")) {
                    textView.setText("Please set your Gemini API Key in MainActivity.java");
                    speak("Please set your Gemini API Key.");
                    Log.e(TAG, "Gemini API Key not set.");
                    return;
                }
                if (generativeModelFutures == null) {
                     initializeGemini(); // Initialize if not already
                }
                if (speechRecognizer != null) {
                    Log.d(TAG, "Starting listening...");
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    Log.e(TAG, "Speak button clicked but SpeechRecognizer is null");
                }
            });
        }
        // Initialize Gemini Model (can be done on first use or here)
        if (!GEMINI_API_KEY.equals("YOUR_API_KEY")) {
            initializeGemini();
        } else {
            Log.w(TAG, "Gemini API Key is not set. Gemini features will be disabled.");
            textView.setText("Warning: Gemini API Key not set.");
        }
    }

    private void initializeGemini() {
        if (GEMINI_API_KEY.equals("YOUR_API_KEY")) {
            Log.e(TAG, "Cannot initialize Gemini: API Key is not set.");
            return;
        }
        try {
            GenerativeModel gm = new GenerativeModel(
                "gemini-pro", // model name
                GEMINI_API_KEY
            );
            generativeModelFutures = GenerativeModelFutures.from(gm);
            Log.i(TAG, "GenerativeModel initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing GenerativeModel: " + e.getMessage(), e);
            textView.setText("Error initializing Gemini: " + e.getMessage());
            speak("Error initializing Gemini.");
        }
    }


    private void callGeminiApi(String query) {
        if (generativeModelFutures == null) {
            Log.e(TAG, "Gemini model not initialized. Cannot make API call.");
            textView.setText("Gemini not ready. Please try again after initialization.");
            speak("Gemini is not ready yet.");
            // Attempt to reinitialize if it failed earlier or was not called
            if (!GEMINI_API_KEY.equals("YOUR_API_KEY")) {
                 initializeGemini();
                 if (generativeModelFutures == null) return; // Still failed
            } else {
                return;
            }
        }

        Log.i(TAG, "Sending to Gemini: " + query);
        textView.setText("Asking Gemini..."); // Update UI

        Content content = new Content.Builder().addText(query).build();

        ListenableFuture<GenerateContentResponse> future = generativeModelFutures.generateContent(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String geminiResponseText = "";
                if (result != null && result.getText() != null) {
                    geminiResponseText = result.getText();
                    Log.i(TAG, "Gemini Response: " + geminiResponseText);
                    final String finalResponse = geminiResponseText; // Ensure final for UI thread
                     runOnUiThread(() -> { // Ensure UI updates are on the main thread
                        textView.setText("Gemini: " + finalResponse);
                        speak(finalResponse);
                    });
                } else {
                    Log.w(TAG, "Gemini response was empty or null.");
                     runOnUiThread(() -> {
                        textView.setText("Gemini: Received no response or empty response.");
                        speak("I did not get a clear response from Gemini.");
                    });
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini API call failed: " + t.getMessage(), t);
                final String errorMessage = "Gemini API Error: " + t.getMessage();
                runOnUiThread(() -> { // Ensure UI updates are on the main thread
                    textView.setText(errorMessage);
                    speak(errorMessage);
                });
            }
        }, executorService); // Use the executor for the callback
    }

    private void speak(String text) {
        if (textToSpeech == null) {
            Log.e(TAG, "TTS speak called but textToSpeech is null.");
            textView.setText("TTS Error: Not initialized."); // Update UI about TTS issue
            return;
        }
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        // Check if TTS is initialized properly before speaking
        if (textToSpeech.getEngines().isEmpty()) {
            Log.e(TAG, "TTS: No engine found");
            textView.setText("TTS: No engine found");
            return;
        }
        Log.i(TAG, "TTS Speaking: " + text);
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UniqueID");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called.");
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            Log.d(TAG, "SpeechRecognizer destroyed.");
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            Log.d(TAG, "TextToSpeech destroyed.");
        }
        if (executorService != null) {
            executorService.shutdown();
            Log.d(TAG, "ExecutorService shutdown.");
        }
    }

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error for speech recognition"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout for speech recognition"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No speech match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "RecognitionService busy"; break;
            case SpeechRecognizer.ERROR_SERVER: message = "Server error for speech recognition"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
            default: message = "Unknown speech error"; break;
        }
        return message;
    }
}
