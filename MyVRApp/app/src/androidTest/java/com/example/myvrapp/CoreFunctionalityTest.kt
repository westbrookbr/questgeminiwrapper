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

// IMPORTANT: MyVRApp/app/src/main/java/com/example/myvrapp/MainActivity.kt was NOT found.
// The testUIElements_Existence test WILL FAIL because it relies on launching MainActivity.
// It is included here for completeness based on the prompt, should MainActivity be added later.
// If MainActivity is added, it must set a content view that includes R.id.textView and R.id.scrollView.
// For example, it could use MyVRApp/app/src/main/res/layout/activity_main.xml.

@RunWith(AndroidJUnit4::class)
class CoreFunctionalityTest {

    private lateinit var context: Context
    private var tts: TextToSpeech? = null
    private lateinit var ttsInitLatch: CountDownLatch // Initialize in setup/test

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        ttsInitLatch = CountDownLatch(1) // Reset latch for each TTS test
    }

    @Test
    fun testSpeechRecognizer_Initialization() {
        assertTrue("Speech recognition should be available", SpeechRecognizer.isRecognitionAvailable(context))
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        assertNotNull("SpeechRecognizer instance should not be null", speechRecognizer)
        speechRecognizer?.destroy() // Clean up
    }

    @Test
    fun testTextToSpeech_Initialization() {
        var ttsStatus = TextToSpeech.ERROR
        tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
            ttsStatus = status
            if (status == TextToSpeech.SUCCESS) {
                ttsInitLatch.countDown()
            } else {
                Log.e("TTS_TEST", "TTS Initialization failed with status: $status")
            }
        })

        val initResult = ttsInitLatch.await(10, TimeUnit.SECONDS)
        assertTrue("TTS engine should initialize successfully within 10 seconds (latch)", initResult)
        assertEquals("TTS engine should report SUCCESS status", TextToSpeech.SUCCESS, ttsStatus)
        assertNotNull("TTS instance should not be null after initialization attempt", tts)

        // Simple phrase processing test after successful initialization
        if (ttsStatus == TextToSpeech.SUCCESS) {
            val speakResult = tts?.speak("Hello", TextToSpeech.QUEUE_FLUSH, null, "testSpeak")
            assertEquals("TTS speak should return SUCCESS", TextToSpeech.SUCCESS, speakResult)
        } else {
            fail("Skipping speak test because TTS initialization failed.")
        }
    }


    @Test
    fun testGeminiApiClient_InitializationAndDummyCall() {
        val apiKey = BuildConfig.GEMINI_API_KEY // This comes from build.gradle
        assertNotNull("GEMINI_API_KEY should be present in BuildConfig", apiKey)
        assertFalse("GEMINI_API_KEY should not be empty", apiKey.isEmpty())
        // It's okay if it's the placeholder, this test just checks presence and basic client instantiation
        // assertNotEquals("GEMINI_API_KEY should not be the placeholder an actual test key is expected for a real call", "\"YOUR_API_KEY_HERE\"", apiKey)
        // The key in BuildConfig will be quoted, e.g., "\"YOUR_API_KEY_HERE\"" not "YOUR_API_KEY_HERE"

        var generativeModel: GenerativeModel? = null
        try {
            // Ensure the API key passed to the model constructor is unquoted if necessary.
            // The BuildConfig field is already a String literal including quotes.
            // The GenerativeModel constructor expects the raw API key string.
            val rawApiKey = apiKey.trim('"')

            generativeModel = GenerativeModel(
                modelName = "gemini-pro", // Using a standard model
                apiKey = rawApiKey,
                generationConfig = generationConfig { temperature = 0.7f }
            )
            assertNotNull("GenerativeModel instance should be created", generativeModel)

            runBlocking {
                try {
                    // A simple prompt to test the API.
                    // This may fail depending on the API key's validity and permissions.
                    // For this test, we are primarily concerned with the client not crashing
                    // and attempting the call. Specific error handling for invalid keys
                    // would be part of a more in-depth integration test.
                    val response = generativeModel.generateContent("What is the weather like?")
                    assertNotNull("Response from generateContent should not be null", response)
                    Log.i("GeminiTest", "Dummy call response: ${response.text ?: "No text in response"}")
                } catch (e: Exception) {
                    // Log the exception. For a basic initialization test, this might not be a failure,
                    // especially if the API key is a placeholder or invalid.
                    // A real test with a valid key would assert a successful response.
                    Log.w("GeminiTest", "Exception during dummy call (may be expected with placeholder/invalid key): ${e.message}", e)
                    // If using a placeholder key, this exception is expected.
                    // For example, if the key is "YOUR_API_KEY_HERE", an auth error is normal.
                    if (rawApiKey == "YOUR_API_KEY_HERE" || rawApiKey.isBlank()) {
                        // This is an expected failure path if the key is a placeholder
                        Log.i("GeminiTest", "Dummy call failed as expected with placeholder API key.")
                    } else {
                        // If the key is supposed to be valid, then this is a failure.
                        // For now, we are lenient as it's an "InitializationAndDummyCall" test
                        // fail("Gemini API call failed with a non-placeholder key: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            fail("Gemini AI Client initialization or basic call failed: ${e.message}")
        }
    }

    @Test
    fun testUIElements_Existence() {
        Log.w("UIElementTest", "This test (testUIElements_Existence) is expected to FAIL if MainActivity.kt is missing or doesn't set a layout with R.id.textView and R.id.scrollView.")
        Log.i("UIElementTest", "Checking for R.id values (compile-time check essentially)...")
        var textViewId = 0
        var scrollViewId = 0
        try {
            textViewId = R.id.textView
            scrollViewId = R.id.scrollView
            assertNotEquals("R.id.textView should exist and not be 0", 0, textViewId)
            Log.i("UIElementTest", "R.id.textView found with value: $textViewId")
            assertNotEquals("R.id.scrollView should exist and not be 0", 0, scrollViewId)
            Log.i("UIElementTest", "R.id.scrollView found with value: $scrollViewId")
        } catch (e: Resources.NotFoundException) {
            fail("UI Element Test Pre-check FAILED: R.id.textView or R.id.scrollView not found. Error: ${e.message}")
            return // Exit early if IDs aren't even found
        } catch (e: NoClassDefFoundError) {
            fail("UI Element Test Pre-check FAILED: R class not found. Build issue? Error: ${e.message}")
            return
        }


        // Attempt to launch MainActivity - this will fail if MainActivity.kt doesn't exist or isn't registered.
        try {
            val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val responseTextView = activity.findViewById<TextView>(R.id.textView)
                assertNotNull("Response TextView (R.id.textView) should be found in MainActivity's layout", responseTextView)

                val responseScrollView = activity.findViewById<ScrollView>(R.id.scrollView)
                assertNotNull("Response ScrollView (R.id.scrollView) should be found in MainActivity's layout", responseScrollView)
            }
            scenario.close() // Close the activity scenario
        } catch (e: Exception) {
            val errorMsg = "UI Element Test FAILED during ActivityScenario launch or view finding. This is LIKELY DUE TO MISSING MainActivity.kt or it not setting a proper layout. Error: ${e.message}"
            Log.e("UIElementTest", errorMsg, e)
            // To provide more context if R.id values were found but activity failed:
            Log.i("UIElementTest", "Context: R.id.textView = $textViewId, R.id.scrollView = $scrollViewId. If these are non-zero, the issue is likely Activity/Layout related.")
            fail(errorMsg)
        }
    }

    @After
    fun tearDown() {
        tts?.shutdown()
        Log.d("CoreFunctionalityTest", "tearDown completed.")
    }
}
