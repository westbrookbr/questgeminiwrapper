// MainActivity.java
package com.example.myvrapp; // Ensure this matches your project's package name

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

    // TODO: Replace with your actual Gemini API Key
    // IMPORTANT: In a production app, you should fetch this securely (e.g., from a backend server)
    // and not hardcode it directly in your client-side code.
    private static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE";

    private GenerativeModelFutures model;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Assumes res/layout/activity_main.xml exists

        textView = findViewById(R.id.textView);
        speakButton = findViewById(R.id.speakButton);

        executorService = Executors.newSingleThreadExecutor();

        // Initialize Gemini Model
        try {
            GenerativeModel baseModel = new GenerativeModel("gemini-pro", GEMINI_API_KEY);
            model = GenerativeModelFutures.from(baseModel);
        } catch (Exception e) {
            textView.setText("Error initializing Gemini Model: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US); // Or Locale.getDefault()
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textView.setText("TTS: Language not supported or data missing.");
                }
            } else {
                textView.setText("TTS: Initialization failed.");
            }
        });

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { textView.setText("Listening..."); }
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
                textView.setText("Speech Rec Error: " + errorMessage);
                speak("Speech recognition error. Please try again.");
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    textView.setText("You said: " + recognizedText);
                    callGeminiAPI(recognizedText); // Send recognized text to Gemini API
                } else {
                    textView.setText("No speech recognized.");
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

    // Method to call Gemini API
    private void callGeminiAPI(String prompt) {
        if (model == null) {
            textView.setText("Gemini model not initialized.");
            speak("Gemini service is not ready. Please check the API key.");
            return;
        }

        textView.setText("Thinking...");
        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String geminiResponse = result.getText();
                if (geminiResponse != null && !geminiResponse.isEmpty()) {
                    runOnUiThread(() -> textView.setText("Gemini: " + geminiResponse));
                    speak(geminiResponse);
                } else {
                    runOnUiThread(() -> textView.setText("Gemini: No response."));
                    speak("I'm sorry, I couldn't get a response from Gemini.");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    textView.setText("Gemini Error: " + t.getMessage());
                    speak("There was an error communicating with Gemini. Please try again.");
                });
                t.printStackTrace();
            }
        }, executorService);
    }

    // Method to speak text using TextToSpeech
    private void speak(String text) {
        if (textToSpeech != null) {
            // Stop current speech before starting new one for immediate feedback
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
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
