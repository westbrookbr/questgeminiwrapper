# Gemini VR Wrapper Application

## Overview

This project is a VR-ready Android application designed to integrate Google's Gemini AI. It provides a foundational framework for sending requests to the Gemini API and displaying responses within a VR environment. The project emphasizes secure API key management, automated build and verification processes, enhanced error handling, and robust deployment scripts.

## Features

*   **Command-line driven APK generation:** Scripts for building debug and release APKs.
*   **Secure API Key Injection:** Prioritized sourcing of `GEMINI_API_KEY`.
*   **Automated APK Verification:** The `build_and_verify.sh` script checks for APK existence, size, and valid signing.
*   **Automated Technical Validation:** Instrumentation tests validate core functionalities like Speech Recognizer, TTS, Gemini API client, and UI elements.
*   **Scrollable Text Display:** Main layout includes a ScrollView for long API responses.
*   **Enhanced Error Handling:** Scripts now use a common error handler (`error_handler.sh`) for consistent, timestamped error reporting and immediate exit on failure.
*   **Automated Release Pipeline:** Includes scripts for release notes generation, strict version tagging, and deployment to Meta Quest and Firebase App Distribution.

## Prerequisites

*   **Android SDK:** API Level 29 or higher.
*   **Java Development Kit (JDK):** Version 11 or 17.
*   **(Optional) Android Studio:** For development and SDK management.
*   **`apksigner` tool:** For APK signature verification (part of Android SDK Build-Tools).
*   **`git`:** For version control and tagging features.
*   **(For Meta Quest Deployment) `ovr-platform-util`:** Meta's command-line tool for uploading builds.
*   **(For Firebase Deployment) `firebase-tools`:** Firebase CLI for app distribution.

## Setup & Configuration

### 1. Cloning the Repository

```bash
git clone https://your-repository-url-here/gemini-vr-wrapper.git
cd gemini-vr-wrapper
```
(Replace with the actual repository URL).

### 2. API Key Configuration (`GEMINI_API_KEY`)

The application requires a Gemini API key. It's sourced in the following order of priority:

1.  **Environment Variable (Recommended):**
    ```bash
    export GEMINI_API_KEY="YOUR_ACTUAL_GEMINI_API_KEY"
    ```
    Add to `~/.bashrc`, `~/.zshrc`, etc., for persistence.

2.  **`gradle.properties` file:**
    In `MyVRApp/gradle.properties`:
    ```properties
    GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY
    ```
    **Warning:** Ensure `MyVRApp/gradle.properties` is in `.gitignore` if it contains sensitive keys.

### 3. Signing Configuration for Release Builds

In `MyVRApp/gradle.properties`:
```properties
RELEASE_STORE_FILE=your_keystore_filename.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```
Place your keystore in `MyVRApp/app` or provide a full path. Ensure `MyVRApp/gradle.properties` is in `.gitignore`.

## Building the Application

All build commands should be run from the `MyVRApp` directory. Scripts now use `error_handler.sh` for robust error reporting.

### 1. Debug Build (using `build_with_api_key.sh`)

Sets `GEMINI_API_KEY` for the build command.
```bash
cd MyVRApp
./build_with_api_key.sh YOUR_GEMINI_API_KEY
```
Output: `MyVRApp/app/build/outputs/apk/debug/app-debug.apk`.

### 2. Release Build, Verification, and Testing (using `build_and_verify.sh`)

**Before running:**
*   Set `GEMINI_API_KEY` (environment or `gradle.properties`).
*   Set signing properties in `MyVRApp/gradle.properties`.

```bash
cd MyVRApp
./build_and_verify.sh
```
This script will:
1.  Build the release APK.
2.  Verify the APK.
3.  Run instrumentation tests.
4.  Report success or failure (exiting on error).
5.  After success, prompt for and apply a Git version tag (see "Automated Version Tagging").
Output: `MyVRApp/app/build/outputs/apk/release/app-release.apk`.

### 3. Manual Gradle Commands

(Ensure `GEMINI_API_KEY` and signing properties are set appropriately.)
*   **Debug APK:** `./gradlew assembleDebug`
*   **Release APK:** `./gradlew assembleRelease`
*   **Instrumentation Tests:** `./gradlew connectedAndroidTest` (requires connected device/emulator)

## Interpreting Script Output

Scripts provide clear messages. With the new error handling:
*   Success messages indicate completion of stages.
*   Error messages will be prefixed with `[YYYY-MM-DD HH:MM:SS] ERROR in <script_name> at line <line_number>: <error_message>` and the script will halt.

## Automated Release Pipeline

### Release Notes Generation (`generate_release_notes.sh`)

Generates `MyVRApp/RELEASE_NOTES.md` from Git commit history since the last tag.
```bash
cd MyVRApp
./generate_release_notes.sh
```
If `MyVRApp/RELEASE_NOTES.md` exists, it will be used by `deploy_alpha.sh` for Firebase deployments.

### Automated Version Tagging (in `build_and_verify.sh`)

After a successful build and tests, `build_and_verify.sh` prompts for a Git version tag.
*   **Format Requirement:** Tags must follow strict semantic versioning: `vX.Y.Z` (e.g., `v1.0.0`, `v1.2.3`). Invalid formats will cause an error and halt the script.
*   **Pre-checks:** The script checks if the tag already exists locally or on the remote `origin`. If it exists, an error is reported, and the script halts.
*   **Process:**
    1.  Creates the tag locally (`git tag <tag>`).
    2.  Pushes the tag to `origin` (`git push origin <tag>`).
*   **Skipping:** Press Enter without a tag name to skip.
*   **Error Handling:** Invalid format, existing tags, or failures in `git tag` or `git push` commands will now call `handle_error` and halt the script.

### Deployment Script (`deploy_alpha.sh`)

Automates APK deployment.
```bash
cd MyVRApp
./deploy_alpha.sh <path_to_apk> <target_type> [options]
```
Features timestamped logging and robust error handling.

#### Supported Targets:

1.  **Local Deployment:**
    *   **Command:** `./deploy_alpha.sh app/build/outputs/apk/release/app-release.apk local /path/to/your/share_directory`
    *   **Action:** Copies APK to the specified local directory.

2.  **Meta Quest Deployment:**
    *   **Command:** `./deploy_alpha.sh <path_to_apk> meta_quest <your_app_id>`
    *   **Action:** Uploads the APK to the Meta Quest Store (Alpha channel by default) using `ovr-platform-util`. **This now performs an actual upload.**
    *   **Required Environment Variables:**
        *   `OVR_APP_ID`: Your Meta Quest Application ID.
        *   `OVR_APP_SECRET`: Your Meta Quest Application Secret.
        *   `OVR_ACCESS_TOKEN`: Your Meta Quest User Access Token.
    *   **Obtaining Credentials:**
        *   **`OVR_APP_ID` and `OVR_APP_SECRET`:**
            1.  Go to your Meta Quest Developer Dashboard: [https://developer.oculus.com/manage/](https://developer.oculus.com/manage/)
            2.  Select your application.
            3.  Navigate to the 'API' or 'App Credentials' section.
            4.  Your App ID and App Secret will be listed there.
        *   **`OVR_ACCESS_TOKEN`:**
            1.  This token is typically obtained using the `ovr-platform-util` or via Meta's developer tools.
            2.  Run `ovr-platform-util get-access-token` (or a similar command specific to your `ovr-platform-util` version) and follow the prompts. This usually involves logging into your Meta developer account.
            3.  The token is long-lived but can expire. Store it securely.
    *   **Environment Variable Setup Example:**
        ```bash
        export OVR_APP_ID="YOUR_APP_ID"
        export OVR_APP_SECRET="YOUR_APP_SECRET"
        export OVR_ACCESS_TOKEN="YOUR_ACCESS_TOKEN"
        ```
        Add these to your shell's profile (e.g., `~/.bashrc`, `~/.zshrc`) or your CI/CD system's secret management.
    *   **Optional Environment Variable:**
        *   `OVR_PLATFORM_UTIL_PATH`: Set this if `ovr-platform-util` is not in your system's `PATH`.

3.  **Firebase App Distribution:**
    *   **Command:** `./deploy_alpha.sh <path_to_apk> firebase`
    *   **Action:** Distributes the APK via Firebase App Distribution. **This now performs an actual upload.** Release notes from `MyVRApp/RELEASE_NOTES.md` will be used if the file exists.
    *   **Required Environment Variables:**
        *   `FIREBASE_APP_ID`: Your Firebase Application ID.
    *   **Conditional Environment Variable:**
        *   `FIREBASE_TOKEN`: Your Firebase CLI refresh token. Required for CI or non-interactive environments. For local interactive use, `firebase login` is sufficient.
    *   **Obtaining Credentials:**
        *   **`FIREBASE_APP_ID`:**
            1.  Go to your Firebase Console: [https://console.firebase.google.com/](https://console.firebase.google.com/)
            2.  Select your Firebase project.
            3.  Click the gear icon (Project settings) next to 'Project Overview'.
            4.  Under the 'General' tab, in the 'Your apps' card, select your specific Android application. The 'App ID' (looking like `1:1234567890:android:abcdef1234567890`) will be listed there.
        *   **`FIREBASE_TOKEN` (for CI/non-interactive use):**
            1.  Install Firebase CLI: `npm install -g firebase-tools` (or see official docs).
            2.  Log in to Firebase for CI: `firebase login:ci`
            3.  This command will print a refresh token. This is your `FIREBASE_TOKEN`. Store it securely.
    *   **Environment Variable Setup Example:**
        ```bash
        export FIREBASE_APP_ID="YOUR_FIREBASE_APP_ID"
        export FIREBASE_TOKEN="YOUR_FIREBASE_CI_TOKEN" # Optional for local use if already logged in via 'firebase login'
        ```
        Add these to your shell's profile or CI/CD secret management.
    *   **Optional Environment Variable:**
        *   `FIREBASE_CLI_PATH`: Set this if the `firebase` CLI tool is not in your system's `PATH`.

## Automated Technical Tests

Located in `MyVRApp/app/src/androidTest/java/com/example/myvrapp/CoreFunctionalityTest.kt`.
Tests validate: Speech Recognizer, TextToSpeech engine, Gemini API client, and core UI elements.
Run via `build_and_verify.sh` or manually:
```bash
cd MyVRApp
./gradlew connectedAndroidTest
```

## Project Structure
(Project structure diagram remains largely the same, ensure `error_handler.sh`, `generate_release_notes.sh`, `deploy_alpha.sh` are listed if not already)
```
MyVRApp/
├── app/
│   ├── ... (main app content)
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
├── error_handler.sh         # Common error handling script
├── build_with_api_key.sh
├── build_and_verify.sh
├── generate_release_notes.sh
├── deploy_alpha.sh
├── RELEASE_NOTES.md         # Generated release notes
└── README.md
```

## Troubleshooting

*   **Enhanced Error Reporting:**
    The build and deployment scripts (`build_and_verify.sh`, `deploy_alpha.sh`, etc.) now use a common error handler. If a step fails, you will see a detailed, timestamped error message in the format:
    `[YYYY-MM-DD HH:MM:SS] ERROR in <script_name> at line <line_number>: <error_message>`
    The script will exit immediately upon such an error, preventing further execution.

*   **Build fails due to missing API key:**
    Ensure `GEMINI_API_KEY` is correctly set (Environment Variable > `gradle.properties`).

*   **Release build fails (signing issues):**
    Verify signing properties in `MyVRApp/gradle.properties` and keystore file path/access.

*   **`apksigner` not found:**
    Ensure Android SDK `build-tools` directory is in PATH, or `ANDROID_HOME`/`ANDROID_SDK_ROOT` is set.

*   **Instrumentation tests fail:**
    Check for connected device/emulator (`adb devices`). Review Logcat for detailed errors. `testUIElements_Existence` depends on `MainActivity.kt` and `activity_main.xml` configuration.

*   **Deployment Failures (Meta Quest or Firebase):**
    *   **Authentication:** Double-check that `OVR_APP_ID`, `OVR_APP_SECRET`, `OVR_ACCESS_TOKEN` (for Meta Quest) or `FIREBASE_APP_ID`, `FIREBASE_TOKEN` (for Firebase CI) are correctly set as environment variables and are valid.
    *   **Tool Paths:** If `ovr-platform-util` or `firebase` CLI are not in your default PATH, ensure you've set `OVR_PLATFORM_UTIL_PATH` or `FIREBASE_CLI_PATH` environment variables respectively.
    *   **Network Issues:** Live deployments require internet access. Check your connection.
    *   **Tool-Specific Errors:** Pay close attention to any error messages output directly by `ovr-platform-util` or `firebase` CLI, as these often provide specific reasons for failure (e.g., invalid app ID, insufficient permissions, quota exceeded).

*   **Tagging Failures (`build_and_verify.sh`):**
    *   **Invalid Format:** Ensure tags are `vX.Y.Z` (e.g., `v1.0.1`).
    *   **Existing Tag:** The script will fail if the tag already exists locally or on `origin`. Choose a unique tag name.
    *   **Git Errors:** Ensure `git` commands can be executed (e.g., you are in a git repository, credentials for pushing to remote are set up if applicable).
```
