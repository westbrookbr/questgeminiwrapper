// MainActivity.java
package com.example.myvrapp; // Ensure this matches your project's package name

import com.example.myvrapp.BuildConfig;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;
import android.app.Activity;
import android.widget.TextView;
import android.widget.Button;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

public class MainActivity extends Activity {
    private TextView textView;
    private Button speakButton;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech textToSpeech;

    private static final String TAG = "MainActivityVR"; // Or any other suitable tag

    // IMPORTANT: In a production app, you should fetch this securely (e.g., from a backend server)
    // and not hardcode it directly in your client-side code.
private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;

    private GenerativeModelFutures model;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Assumes res/layout/activity_main.xml exists

        textView = findViewById(R.id.textView);
        speakButton = findViewById(R.id.speakButton);

    // Log API Key status
    if (GEMINI_API_KEY == null || GEMINI_API_KEY.equals("YOUR_API_KEY_HERE") || GEMINI_API_KEY.isEmpty() || GEMINI_API_KEY.equals("DEFAULT_NO_API_KEY")) { // Added DEFAULT_NO_API_KEY
        android.util.Log.e(TAG, "API Key not loaded or placeholder detected. Please ensure GEMINI_API_KEY is set in gradle.properties.");
        updateUiForState("ERROR", "ERROR: Gemini API Key not configured.");
        // Potentially disable API calling features or inform user
    } else {
        android.util.Log.i(TAG, "Gemini API Key loaded successfully.");
        updateUiForState("IDLE", "Press the button and speak to Gemini");
        // Avoid logging the actual key: android.util.Log.d(TAG, "API Key: " + GEMINI_API_KEY); // Potentially sensitive
    }

        executorService = Executors.newSingleThreadExecutor();

        // Initialize Gemini Model
        try {
            GenerativeModel baseModel = new GenerativeModel("gemini-pro", GEMINI_API_KEY);
            model = GenerativeModelFutures.from(baseModel);
        android.util.Log.i(TAG, "GenerativeModel initialized successfully.");
        } catch (Exception e) {
        android.util.Log.e(TAG, "Error initializing Gemini Model: " + e.getMessage(), e);
            textView.setText("Error initializing Gemini Model: " + e.getMessage());
        // e.printStackTrace(); // Logcat will show this via android.util.Log.e
        }

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            // General note: True spatial audio for TTS in VR would typically require
            // integration with a VR SDK's audio engine, not provided by standard Android TTS.
            if (status == TextToSpeech.SUCCESS) {
                // Using Locale.US as an example. Locale.getDefault() could also be used.
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    android.util.Log.e(TAG, "TTS: Language (Locale.US) data missing or not supported.");
                    updateUiForState("ERROR", "TTS Error: Language data missing or not supported.");
                } else {
                    // Log the actual language being used by TTS.
                    // Note: getVoice().getLocale() might be more accurate if a specific voice was set.
                    // Here, we log the locale we attempted to set.
                    android.util.Log.i(TAG, "TTS initialized successfully. Language set to: " + Locale.US.toString());
                    // Default speech rate, pitch, and voice are used. These can be customized
                    // using textToSpeech.setSpeechRate(), textToSpeech.setPitch(), etc.
                }
            } else {
                android.util.Log.e(TAG, "TTS Initialization failed. Status: " + status);
                updateUiForState("ERROR", "TTS Error: Initialization failed.");
            }
        });

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { updateUiForState("LISTENING", "Listening..."); }
            @Override
            public void onBeginningOfSpeech() { }
            @Override
            public void onRmsChanged(float rmsdB) { }
            @Override
            public void onBufferReceived(byte[] buffer) { }
            @Override
            public void onEndOfSpeech() { }
            @Override
            public void onError(int error) {
                String errorMessage = getErrorText(error);
                updateUiForState("ERROR", "Speech Rec Error: " + errorMessage);
                speak("Speech recognition error. Please try again.");
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    // textView.setText("You said: " + recognizedText); // Original line, will be handled by callGeminiAPI
                    callGeminiAPI(recognizedText); // Send recognized text to Gemini API
                } else {
                    updateUiForState("IDLE", "No speech recognized.");
                    speak("I didn't catch that. Can you please repeat?");
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
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speakButton.setOnClickListener(v -> {
            // Check for RECORD_AUDIO permission before starting recognition
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
            } else {
                speechRecognizer.startListening(speechRecognizerIntent);
            }
        });
    }

  /**
   * Updates the main TextView to reflect the current state of the application.
   * This method ensures that UI updates are performed on the main thread.
   *
   * @param state A string representing the current state (e.g., "LISTENING", "PROCESSING", "IDLE", "ERROR").
   *              This is primarily used for logging and potential future UI changes based on state.
   * @param message The message to display to the user in the TextView.
   */
  private void updateUiForState(String state, String message) {
    android.util.Log.d(TAG, "Updating UI for state: " + state + ", message: " + message);
    runOnUiThread(() -> textView.setText(message));
    // TODO: Add more UI changes based on state if needed, e.g., button text, colors
  }

    // Method to call Gemini API
    private void callGeminiAPI(String prompt) {
        android.util.Log.i(TAG, "Attempting to call Gemini API with prompt: '" + prompt + "'");
        if (model == null) {
            textView.setText("Gemini model not initialized.");
            speak("Gemini service is not ready. Please check the API key.");
            return;
        }

        updateUiForState("PROCESSING", "Thinking...");
        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String geminiResponseText = result.getText(); // Use this variable
                android.util.Log.i(TAG, "Gemini API call successful. Prompt: '" + prompt + "'. Response: '" + geminiResponseText + "'");
                if (geminiResponseText != null && !geminiResponseText.isEmpty()) {
                    updateUiForState("IDLE", "Gemini: " + geminiResponseText);
                    speak(geminiResponseText);
                } else {
                    android.util.Log.w(TAG, "Gemini API call successful but response was null or empty.");
                    updateUiForState("IDLE", "Gemini: No response.");
                    speak("I'm sorry, I couldn't get a response from Gemini.");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                android.util.Log.e(TAG, "Gemini API call failed. Prompt: '" + prompt + "'. Error: " + t.getMessage(), t);
                updateUiForState("ERROR", "Gemini Error: " + t.getMessage());
                speak("There was an error communicating with Gemini. Please try again.");
                // t.printStackTrace(); // Logcat will show this via android.util.Log.e
            }
        }, executorService);
    }

    // Method to speak text using TextToSpeech
    private void speak(String text) {
        if (textToSpeech != null && text != null && !text.isEmpty()) {
            // Stop current speech before starting new one for immediate feedback.
            // This is useful if a new TTS request comes in while a previous one is still speaking.
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            // QUEUE_FLUSH drops all previous items in the queue and plays the new one immediately.
            // QUEUE_ADD would add it to the end of the queue.
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // Helper method to get human-readable error messages for SpeechRecognizer
    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "RecognitionService busy"; break;
            case SpeechRecognizer.ERROR_SERVER: message = "Error from server"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
            default: message = "Didn't understand, please try again."; break;
        }
        return message;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, restart listening
                speechRecognizer.startListening(speechRecognizerIntent);
            } else {
                textView.setText("Permission denied: RECORD_AUDIO is required.");
                speak("Microphone permission is required to use this application.");
            }
        }
    }
}
