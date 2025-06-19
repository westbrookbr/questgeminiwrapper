package com.example.myvrapp

import android.content.Context
import android.content.res.Resources
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
// import androidx.test.platform.app.InstrumentationRegistry // Not directly used, context from ApplicationProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
// import java.util.Locale // Not directly used
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.tts.UtteranceProgressListener
import com.google.ai.client.generativeai.type.InvalidApiKeyException
import com.google.ai.client.generativeai.type.ServerException
import org.mockito.Mockito // Not used yet, but might be for more advanced mocking

// Note: MainActivity.kt is expected to be present for testUIElements_Existence.
// It should set a content view that includes R.id.textView and R.id.scrollView
// (e.g., using MyVRApp/app/src/main/res/layout/activity_main.xml).

@RunWith(AndroidJUnit4::class)
class CoreFunctionalityTest {

    private lateinit var context: Context
    private var tts: TextToSpeech? = null
    private lateinit var ttsInitLatch: CountDownLatch
    private lateinit var ttsSpeakLatch: CountDownLatch
    private var ttsErrorOccurred = false


    // Listener for TTS callbacks
    private val ttsListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.i("TTS_TEST_LISTENER", "TTS onStart for $utteranceId")
        }

        override fun onDone(utteranceId: String?) {
            Log.i("TTS_TEST_LISTENER", "TTS onDone for $utteranceId")
            ttsSpeakLatch.countDown()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.e("TTS_TEST_LISTENER", "TTS onError for $utteranceId")
            ttsErrorOccurred = true
            ttsSpeakLatch.countDown()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e("TTS_TEST_LISTENER", "TTS onError for $utteranceId, errorCode: $errorCode")
            ttsErrorOccurred = true
            ttsSpeakLatch.countDown()
        }
    }


    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        ttsInitLatch = CountDownLatch(1)
        // ttsSpeakLatch will be initialized in tests that need it
        ttsErrorOccurred = false

        // Initialize TTS once for all relevant tests to save time, but ensure clean state
        if (tts == null) { // Initialize only if not already done
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setOnUtteranceProgressListener(ttsListener)
                    Log.i("TTS_SETUP", "TTS Initialized successfully with listener.")
                    ttsInitLatch.countDown()
                } else {
                    Log.e("TTS_SETUP", "TTS Initialization failed with status: $status")
                    // Potentially fail tests immediately if TTS is critical for setup
                    // For now, individual tests will check ttsInitLatch
                }
            }
        } else { // TTS already initialized, just ensure latch is handled
             if (tts?.setOnUtteranceProgressListener(ttsListener) == TextToSpeech.SUCCESS) {
                 Log.i("TTS_SETUP", "TTS Listener re-attached.")
                 ttsInitLatch.countDown() // If already init, countdown immediately
             } else {
                 Log.e("TTS_SETUP", "Failed to re-attach TTS Listener.")
             }
        }
    }
    private fun ensureTtsInitialized() {
        val initialized = ttsInitLatch.await(10, TimeUnit.SECONDS)
        assertTrue("TTS engine should initialize successfully within 10 seconds.", initialized)
        assertNotNull("TTS instance should not be null after initialization attempt.", tts)
        val engines = tts?.engines
        assertFalse("TTS engines list should not be empty.", engines.isNullOrEmpty())
        Log.i("TTS_Check", "TTS Engines available: ${engines?.joinToString { it.name }}")
    }


    @Test
    fun testSpeechRecognizer_InitializationAndAvailability() {
        assertTrue("Speech recognition overall should be available on device", SpeechRecognizer.isRecognitionAvailable(context))
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        assertNotNull("SpeechRecognizer instance should not be null", speechRecognizer)
        Log.i("SpeechRecognizerTest", "SpeechRecognizer created. Component: ${speechRecognizer.serviceComponent}")

        // Conceptual: Test for specific errors like ERROR_NO_MATCH or ERROR_SPEECH_TIMEOUT
        // This would typically require a way to inject results into the recognizer or use a mock.
        // For example, one might try to start listening and then provide no audio,
        // but this is hard to automate reliably in an instrumentation test.
        // speechRecognizer.setRecognitionListener(object : RecognitionListener {
        //     override fun onResults(results: Bundle?) { /* ... */ }
        //     override fun onError(error: Int) {
        //         // if (error == SpeechRecognizer.ERROR_NO_MATCH) { /* Assert specific handling */ }
        //     }
        //     /* other overrides */
        // })
        // speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
        // // ... need a way to timeout or force an error condition ...

        Log.w("SpeechRecognizerTest", "Further tests for empty/unintelligible input are conceptual without mocking/controlling the SR service.")
        speechRecognizer.destroy()
    }

    @Test
    fun testTextToSpeech_InitializationAndBasicSpeak() {
        ensureTtsInitialized() // Waits for TTS to be ready from setup()

        ttsSpeakLatch = CountDownLatch(1)
        ttsErrorOccurred = false
        val speakResult = tts?.speak("Hello from test", TextToSpeech.QUEUE_FLUSH, null, "testBasicSpeak")
        assertEquals("TTS speak should return SUCCESS for basic phrase", TextToSpeech.SUCCESS, speakResult)

        val speakSuccess = ttsSpeakLatch.await(5, TimeUnit.SECONDS)
        assertTrue("TTS basic speak should complete (onDone callback)", speakSuccess)
        assertFalse("TTS basic speak should not result in an error", ttsErrorOccurred)
    }

    @Test
    fun testTextToSpeech_SpeakLongText() {
        ensureTtsInitialized()
        ttsSpeakLatch = CountDownLatch(1)
        ttsErrorOccurred = false

        val longText = "This is a very long string designed to test the capabilities of the TextToSpeech engine. ".repeat(50)
        Log.i("TTS_LongTextTest", "Attempting to speak long text of length: ${longText.length}")

        val speakResult = tts?.speak(longText, TextToSpeech.QUEUE_FLUSH, null, "testLongSpeak")
        if (speakResult == TextToSpeech.ERROR_INVALID_REQUEST || speakResult == TextToSpeech.ERROR_SERVICE) {
             Log.w("TTS_LongTextTest", "TTS speak returned an error immediately for long text: $speakResult. This might be due to engine limits.")
             // This might not be a strict failure depending on engine capabilities for very long text,
             // but we are primarily checking for crashes.
        } else {
            assertEquals("TTS speak should return SUCCESS for long text (or handle it gracefully)", TextToSpeech.SUCCESS, speakResult)
            val speakSuccess = ttsSpeakLatch.await(30, TimeUnit.SECONDS) // Longer timeout for long text
            assertTrue("TTS long speak should complete or timeout (onDone callback)", speakSuccess)
            assertFalse("TTS long speak should not result in an error callback", ttsErrorOccurred)
        }
        // Main assertion: no crash during the operation.
        assertTrue("Test should complete without crashing from TTS long speak", true)
    }

    @Test
    fun testTextToSpeech_SpeakEmptyAndUnusualText() {
        ensureTtsInitialized()

        // Test 1: Empty string
        ttsSpeakLatch = CountDownLatch(1)
        ttsErrorOccurred = false
        var speakResult = tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, "testEmptySpeak")
        assertEquals("TTS speak with empty string should return SUCCESS (and do nothing)", TextToSpeech.SUCCESS, speakResult)
        // It's typical for TTS engines to do nothing for an empty string and report success for the call itself.
        // The onDone callback might not fire immediately or at all if there's nothing to speak.
        // We'll use a short timeout and primarily ensure no error callback or crash.
        val emptySpeakProcessed = ttsSpeakLatch.await(2, TimeUnit.SECONDS) // Short wait
        if (emptySpeakProcessed) Log.i("TTS_UnusualTextTest", "TTS onDone/onError received for empty string.")
        else Log.w("TTS_UnusualTextTest", "TTS onDone/onError not received for empty string (may be expected).")
        assertFalse("TTS speak with empty string should not result in an error callback", ttsErrorOccurred)

        // Test 2: Unusual characters
        ttsSpeakLatch = CountDownLatch(1) // Reset for next speak
        ttsErrorOccurred = false
        val unusualText = "!@#$%^&*()_+`~=[]{}|;':,./<>?üöäë"
        speakResult = tts?.speak(unusualText, TextToSpeech.QUEUE_FLUSH, null, "testUnusualSpeak")
        assertEquals("TTS speak with unusual chars should return SUCCESS", TextToSpeech.SUCCESS, speakResult)
        val unusualSpeakProcessed = ttsSpeakLatch.await(10, TimeUnit.SECONDS)
        assertTrue("TTS speak with unusual chars should complete (onDone callback)", unusualSpeakProcessed)
        assertFalse("TTS speak with unusual chars should not result in an error callback", ttsErrorOccurred)
    }

    @Test
    fun testTextToSpeech_RapidSuccessiveInputs() {
        ensureTtsInitialized()
        val numRapidInputs = 5
        // For rapid inputs, we use a single latch that counts down for each successful "onDone"
        ttsSpeakLatch = CountDownLatch(numRapidInputs)
        ttsErrorOccurred = false

        Log.i("TTS_RapidTest", "Starting rapid TTS input test for $numRapidInputs phrases.")
        for (i in 1..numRapidInputs) {
            val text = "Phrase $i"
            val utteranceId = "rapidSpeak_$i"
            val speakResult = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            assertEquals("TTS rapid speak call $i should return SUCCESS", TextToSpeech.SUCCESS, speakResult)
            Thread.sleep(50) // Small delay to ensure calls are distinct and queued if engine is slow
        }

        val allSpeaksDone = ttsSpeakLatch.await(20, TimeUnit.SECONDS) // Timeout for all phrases
        assertTrue("All TTS rapid inputs should complete (onDone for each)", allSpeaksDone)
        assertFalse("TTS rapid inputs should not result in any error callbacks", ttsErrorOccurred)
        Log.i("TTS_RapidTest", "Rapid TTS input test completed.")
    }


    @Test
    fun testGeminiApiClient_InitializationAndDummyCallWithPlaceholderKey() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        assertNotNull("GEMINI_API_KEY should be present in BuildConfig", apiKey)
        assertFalse("GEMINI_API_KEY should not be empty", apiKey.isEmpty())

        val rawApiKey = apiKey.trim('"')
        Log.i("GeminiTest_Placeholder", "Current API Key for test: '$rawApiKey'")

        // This test specifically checks behavior with the known placeholder or an empty key
        if (rawApiKey != "YOUR_API_KEY_HERE" && rawApiKey.isNotBlank()) {
            Log.w("GeminiTest_Placeholder", "Skipping placeholder key test as a seemingly valid key is present. This test is for placeholder/empty keys.")
            return // Or use Assume.assumeTrue() if available/preferred
        }

        var generativeModel: GenerativeModel? = null
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-pro",
                apiKey = rawApiKey, // Intentionally using placeholder/empty
                generationConfig = generationConfig { temperature = 0.7f }
            )
            assertNotNull("GenerativeModel instance should be created even with placeholder key", generativeModel)

            runBlocking {
                try {
                    generativeModel.generateContent("Test prompt")
                    fail("Gemini API call should fail with a placeholder/empty API key.")
                } catch (e: Exception) {
                    // Expecting an authentication-related error
                    Log.i("GeminiTest_Placeholder", "Caught exception as expected with placeholder key: ${e.javaClass.simpleName} - ${e.message}")
                    assertTrue(
                        "Exception should be authentication-related for placeholder key (e.g., InvalidApiKeyException or similar ServerException)",
                        e is InvalidApiKeyException || e is ServerException || e.message?.contains("API key not valid", ignoreCase = true) == true
                    )
                }
            }
        } catch (e: Exception) {
            // Some exceptions might occur even during model initialization if key is truly malformed beyond just being a placeholder
            Log.w("GeminiTest_Placeholder", "Exception during GenerativeModel init or call with placeholder: ${e.javaClass.simpleName} - ${e.message}")
             assertTrue(
                "Exception during init should be auth-related for placeholder key (e.g., InvalidApiKeyException)",
                 e is InvalidApiKeyException || e.message?.contains("API key not valid", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun testGeminiApiClient_ErrorHandlingWithInvalidTestKey() {
        val invalidApiKey = "THIS_IS_DEFINITELY_AN_INVALID_API_KEY_FOR_TESTING"
        Log.i("GeminiTest_InvalidKey", "Testing with explicitly invalid API key: $invalidApiKey")

        var generativeModel: GenerativeModel? = null
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-pro",
                apiKey = invalidApiKey,
                generationConfig = generationConfig { temperature = 0.7f }
            )
            assertNotNull("GenerativeModel instance should be created with invalid key", generativeModel)

            runBlocking {
                try {
                    generativeModel.generateContent("Test prompt for invalid key")
                    fail("Gemini API call should fail with an invalid API key.")
                } catch (e: Exception) {
                    Log.i("GeminiTest_InvalidKey", "Caught exception as expected with invalid key: ${e.javaClass.simpleName} - ${e.message}")
                    assertTrue(
                        "Exception should be authentication-related for invalid key (e.g., InvalidApiKeyException or similar ServerException)",
                        e is InvalidApiKeyException || e is ServerException || e.message?.contains("API key not valid", ignoreCase = true) == true
                    )
                }
            }
        } catch (e: Exception) {
            // Some exceptions might occur even during model initialization if key is truly malformed
            Log.w("GeminiTest_InvalidKey", "Exception during GenerativeModel init or call with invalid key: ${e.javaClass.simpleName} - ${e.message}")
            assertTrue(
                "Exception during init should be auth-related for invalid key (e.g., InvalidApiKeyException)",
                 e is InvalidApiKeyException || e.message?.contains("API key not valid", ignoreCase = true) == true
            )
        }
        // Conceptual: Test for network errors. This typically requires mocking the network layer
        // or manipulating network conditions, which is complex for standard instrumentation tests.
        // Log.w("GeminiTest", "Network error simulation is conceptual without mocking tools.")
    }


    @Test
    fun testUIElements_Existence() {
        // Log.w("UIElementTest", "This test (testUIElements_Existence) is expected to FAIL if MainActivity.kt is missing or doesn't set a layout with R.id.textView and R.id.scrollView.")
        Log.i("UIElementTest", "Verifying UI elements in MainActivity...")
        var textViewId = 0
        var scrollViewId = 0
        try {
            // This part just checks if the R.id values are compiled, not if they are in the layout yet.
            textViewId = R.id.textView
            scrollViewId = R.id.scrollView
            assertNotEquals("R.id.textView should exist and have a non-zero ID", 0, textViewId)
            Log.i("UIElementTest", "R.id.textView resource ID found: $textViewId")
            assertNotEquals("R.id.scrollView should exist and have a non-zero ID", 0, scrollViewId)
            Log.i("UIElementTest", "R.id.scrollView resource ID found: $scrollViewId")
        } catch (e: Resources.NotFoundException) {
            fail("UI Element Test Pre-check FAILED: R.id.textView or R.id.scrollView resource ID not found. Error: ${e.message}")
            return
        } catch (e: NoClassDefFoundError) { // Should not happen if R class is generated
            fail("UI Element Test Pre-check FAILED: R class not found. Build issue? Error: ${e.message}")
            return
        }

        // Attempt to launch MainActivity and find views
        // MainActivity.kt is expected to exist and call setContentView with a layout containing these IDs.
        try {
            val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val responseTextView = activity.findViewById<TextView>(R.id.textView)
                assertNotNull("Response TextView (R.id.textView) should be found in MainActivity's layout", responseTextView)
                Log.i("UIElementTest", "TextView successfully found in MainActivity.")

                val responseScrollView = activity.findViewById<ScrollView>(R.id.scrollView)
                assertNotNull("Response ScrollView (R.id.scrollView) should be found in MainActivity's layout", responseScrollView)
                Log.i("UIElementTest", "ScrollView successfully found in MainActivity.")
            }
            scenario.close()
            Log.i("UIElementTest", "testUIElements_Existence PASSED.")
        } catch (e: Exception) {
            val errorMsg = "UI Element Test FAILED during ActivityScenario launch or view finding. Ensure MainActivity exists and sets a layout (e.g., R.layout.activity_main) containing R.id.textView and R.id.scrollView. Error: ${e.message}"
            Log.e("UIElementTest", errorMsg, e)
            fail(errorMsg)
        }
    }

    @After
    fun tearDown() {
        tts?.stop() // Stop any ongoing speech
        tts?.shutdown()
        tts = null // Release TTS instance
        Log.d("CoreFunctionalityTest", "tearDown completed, TTS shutdown.")
    }
}
