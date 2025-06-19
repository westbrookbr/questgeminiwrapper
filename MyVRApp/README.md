# Gemini VR Wrapper Application

## Overview

This project is a VR-ready Android application designed to integrate Google's Gemini AI. It provides a foundational framework for sending requests to the Gemini API and displaying responses within a VR environment. The project emphasizes secure API key management, automated build and verification processes, and core functionality testing.

## Features

*   **Command-line driven APK generation:** Scripts for building debug and release APKs.
*   **Secure API Key Injection:** Prioritized sourcing of `GEMINI_API_KEY`:
    1.  Environment Variable (`GEMINI_API_KEY`)
    2.  `gradle.properties` file (`GEMINI_API_KEY`)
    3.  Hardcoded default (placeholder, not for production)
*   **Automated APK Verification:** The `build_and_verify.sh` script checks for:
    *   APK file existence.
    *   Non-zero APK file size.
    *   Valid APK signing (using `apksigner`).
*   **Automated Technical Validation:** Instrumentation tests (`CoreFunctionalityTest.kt`) validate:
    *   Speech Recognizer initialization.
    *   Text-To-Speech (TTS) engine initialization and basic operation.
    *   Gemini API Client initialization and a dummy API call.
    *   Existence of core UI elements (TextView, ScrollView) within `MainActivity`.
*   **Scrollable Text Display:** The main layout includes a ScrollView to accommodate potentially long responses from the Gemini API.

## Prerequisites

*   **Android SDK:** API Level 29 or higher (recommended for Oculus Quest 2 compatibility).
*   **Java Development Kit (JDK):** Version 11 or 17 is recommended.
*   **(Optional but recommended) Android Studio:** For development, debugging, and easier SDK management.
*   **`apksigner` tool:** Required for APK signature verification. This tool is typically included with the Android SDK Build-Tools. Ensure the build-tools directory is in your system's PATH or that `$ANDROID_HOME`/`$ANDROID_SDK_ROOT` is set correctly.

## Setup & Configuration

### 1. Cloning the Repository

```bash
git clone https://your-repository-url-here/gemini-vr-wrapper.git
cd gemini-vr-wrapper
```
(Replace `https://your-repository-url-here/gemini-vr-wrapper.git` with the actual repository URL).

### 2. API Key Configuration (`GEMINI_API_KEY`)

The application requires a Gemini API key. It's sourced in the following order of priority:

1.  **Environment Variable (Recommended for CI/CD, Secure):**
    Set the `GEMINI_API_KEY` environment variable:
    ```bash
    export GEMINI_API_KEY="YOUR_ACTUAL_GEMINI_API_KEY"
    ```
    To make this persistent across terminal sessions, add it to your shell's configuration file (e.g., `~/.bashrc`, `~/.zshrc`, `~/.profile`), then source the file (e.g., `source ~/.bashrc`). You can also set it per-command: `GEMINI_API_KEY="YOUR_KEY" ./gradlew assembleDebug`.

2.  **`gradle.properties` file:**
    Create or edit the `MyVRApp/gradle.properties` file and add the following line:
    ```properties
    GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY
    ```
    **Warning:** If you use this method, ensure that `MyVRApp/gradle.properties` is listed in your `.gitignore` file to prevent accidentally committing your API key.

### 3. Signing Configuration for Release Builds

Release builds must be signed with a cryptographic key. The configuration for this is in `MyVRApp/app/build.gradle` and expects properties to be available.

**Add your signing configuration to `MyVRApp/gradle.properties`:**

```properties
# In MyVRApp/gradle.properties

# For Release APK Signing
RELEASE_STORE_FILE=your_keystore_filename.jks # e.g., my-release-key.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password

# You can also place your GEMINI_API_KEY here if not using an environment variable
# GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY
```

*   Place your keystore file (e.g., `your_keystore_filename.jks`) in the `MyVRApp/app` directory or provide a relative/absolute path for `RELEASE_STORE_FILE`.
*   Ensure `MyVRApp/gradle.properties` (or at least a version containing sensitive keys) is added to `.gitignore`.
*   You can generate a new keystore using Android Studio (Build > Generate Signed Bundle / APK...) or the `keytool` command-line utility provided by the JDK.

## Building the Application

All build commands should be run from the `MyVRApp` directory.

### 1. Debug Build (using `build_with_api_key.sh`)

This script is a convenience for setting the `GEMINI_API_KEY` as an environment variable specifically for the build command.

```bash
cd MyVRApp
./build_with_api_key.sh YOUR_GEMINI_API_KEY
```
This will produce a debug APK in `MyVRApp/app/build/outputs/apk/debug/`.

### 2. Release Build, Verification, and Testing (using `build_and_verify.sh`)

This is the comprehensive script for generating a production-ready build.
**Before running:**
*   Ensure `GEMINI_API_KEY` is set as an environment variable or in `MyVRApp/gradle.properties`.
*   Ensure `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` are correctly set in `MyVRApp/gradle.properties`.

```bash
cd MyVRApp
./build_and_verify.sh
```
This script will:
1.  Build the release APK.
2.  Verify the APK (existence, non-zero size, valid signature).
3.  Run all instrumentation tests.
4.  Report overall success or indicate the point of failure.
The release APK will be located at `MyVRApp/app/build/outputs/apk/release/app-release.apk`.

### 3. Manual Gradle Commands (Alternative)

You can also use Gradle commands directly:

*   **Build Debug APK:**
    (Ensure `GEMINI_API_KEY` is set via environment variable or in `gradle.properties`)
    ```bash
    cd MyVRApp
    ./gradlew assembleDebug
    ```

*   **Build Release APK:**
    (Ensure `GEMINI_API_KEY` and signing properties are set in `gradle.properties` or passed via `-P` flags)
    ```bash
    cd MyVRApp
    ./gradlew assembleRelease
    ```

*   **Run Instrumentation Tests:**
    (Requires a connected device or emulator)
    ```bash
    cd MyVRApp
    ./gradlew connectedAndroidTest
    ```

## Interpreting `build_and_verify.sh` Output

The `build_and_verify.sh` script provides clear messages for each stage:
1.  Gradle build execution.
2.  APK path definition.
3.  APK existence check.
4.  APK size check.
5.  APK signing verification.
6.  Instrumentation test execution.

*   A final **"MyVRApp Release Build, Verification & Automated Tests ALL SUCCESSFUL!"** message indicates that the APK is built, verified, and core functionalities have passed automated tests.
*   If any stage fails, the script will print an error message indicating where the failure occurred and exit with a non-zero status code.

## Automated Technical Tests

The project includes instrumentation tests located in `MyVRApp/app/src/androidTest/java/com/example/myvrapp/CoreFunctionalityTest.kt`. These tests validate core components of the application:

*   `testSpeechRecognizer_Initialization`: Checks that Android's `SpeechRecognizer` service is available and can be instantiated.
*   `testTextToSpeech_Initialization`: Verifies that the `TextToSpeech` engine initializes correctly and can process a simple speech request.
*   `testGeminiApiClient_InitializationAndDummyCall`: Ensures the Gemini `GenerativeModel` client can be initialized with the provided API key and attempts a basic API call.
*   `testUIElements_Existence`: Checks if `MainActivity` can be launched and if the essential UI elements (`R.id.textView` and `R.id.scrollView`) are present in its layout. This test relies on `MainActivity.kt` and its layout (`R.layout.activity_main`) being correctly configured.

These tests are run automatically by the `build_and_verify.sh` script or can be triggered manually:
```bash
cd MyVRApp
./gradlew connectedAndroidTest
```

## Project Structure

```
MyVRApp/
├── app/                     # Main application module
│   ├── build.gradle         # App-level Gradle build script
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/myvrapp/
│   │   │   │   └── MainActivity.kt  # Main Android Activity
│   │   │   ├── res/                 # Application resources (layouts, values, etc.)
│   │   │   │   ├── layout/activity_main.xml
│   │   │   │   └── values/ids.xml
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/       # Instrumentation tests
│   │       └── java/com/example/myvrapp/
│   │           └── CoreFunctionalityTest.kt
│   └── keystore.properties.example # Example for signing config (actual should be gitignored)
├── build.gradle             # Project-level Gradle build script
├── gradle.properties        # Project-wide Gradle settings (API key, signing info)
├── gradlew                  # Gradle wrapper executable (Linux/macOS)
├── gradlew.bat              # Gradle wrapper executable (Windows)
├── settings.gradle          # Gradle settings
├── build_with_api_key.sh    # Script to build debug APK with API key as argument
└── build_and_verify.sh      # Script to build, verify, and test the release APK
└── README.md                # This file
```

## Troubleshooting

*   **Build fails due to missing API key:**
    Ensure `GEMINI_API_KEY` is correctly set as an environment variable or in `MyVRApp/gradle.properties`. Remember the priority: Environment Variable > `gradle.properties`.

*   **Release build fails (signing issues):**
    *   Verify that `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` are correctly defined in `MyVRApp/gradle.properties`.
    *   Ensure the keystore file specified by `RELEASE_STORE_FILE` exists at the given path and is accessible.
    *   Double-check that the passwords and alias are correct.

*   **`apksigner` not found (during `build_and_verify.sh`):**
    *   Make sure your Android SDK's `build-tools` directory (e.g., `/path/to/android_sdk/build-tools/<version>/`) is added to your system's PATH.
    *   Alternatively, ensure the `ANDROID_HOME` or `ANDROID_SDK_ROOT` environment variable is set, as the script attempts to locate `apksigner` through these paths.

*   **Instrumentation tests fail (`connectedAndroidTest` or via `build_and_verify.sh`):**
    *   Ensure you have an Android device or emulator connected and visible via `adb devices`.
    *   `testUIElements_Existence` specifically might fail if `MainActivity.kt` is not correctly set up, not declared in `AndroidManifest.xml`, or if its layout (`activity_main.xml`) does not contain the expected views (`R.id.textView`, `R.id.scrollView`).
    *   Check Logcat output in Android Studio or via `adb logcat` for more detailed error messages from the tests or application.
```
