#!/bin/bash

# Script to build a release APK for MyVRApp, verify it, and prepare for testing.

echo "Starting MyVRApp release build process..."

# 1. Execute Gradle build
# Ensure this script is run from the MyVRApp directory or that gradlew is accessible.
./gradlew assembleRelease
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    echo "Gradle build failed with status: $BUILD_STATUS"
    exit 1
fi

echo "Gradle build successful."

# 2. Define APK path
APK_PATH="app/build/outputs/apk/release/app-release.apk"
echo "Expected APK path: $APK_PATH"

# 3. Verify APK
# 3a. Check existence
if [ ! -f "$APK_PATH" ]; then
    echo "Build Verification FAILED: APK not found at $APK_PATH"
    exit 1
fi
echo "APK Existence Check: PASSED"

# 3b. Check non-zero size
if [ ! -s "$APK_PATH" ]; then
    echo "Build Verification FAILED: APK is zero size."
    exit 1
fi
echo "APK Size Check: PASSED"

# 3c. Verify signing
echo "Verifying APK signing..."
# Try to find apksigner
APKSIGNER_CMD="apksigner" # Default to hope it's in PATH

# Common structure for Android SDK path
if [ -n "$ANDROID_HOME" ]; then
    # Find the latest build-tools version
    LATEST_BUILD_TOOLS=$(ls -1 "$ANDROID_HOME/build-tools" 2>/dev/null | sort -V | tail -n 1)
    if [ -n "$LATEST_BUILD_TOOLS" ] && [ -f "$ANDROID_HOME/build-tools/$LATEST_BUILD_TOOLS/apksigner" ]; then
        APKSIGNER_CMD="$ANDROID_HOME/build-tools/$LATEST_BUILD_TOOLS/apksigner"
    fi
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    LATEST_BUILD_TOOLS=$(ls -1 "$ANDROID_SDK_ROOT/build-tools" 2>/dev/null | sort -V | tail -n 1)
    if [ -n "$LATEST_BUILD_TOOLS" ] && [ -f "$ANDROID_SDK_ROOT/build-tools/$LATEST_BUILD_TOOLS/apksigner" ]; then
        APKSIGNER_CMD="$ANDROID_SDK_ROOT/build-tools/$LATEST_BUILD_TOOLS/apksigner"
    fi
fi

echo "Using apksigner command: $APKSIGNER_CMD"

$APKSIGNER_CMD verify "$APK_PATH"
SIGN_STATUS=$?

if [ $SIGN_STATUS -ne 0 ]; then
    echo "Build Verification FAILED: APK signing verification failed for $APK_PATH."
    echo "Command used: '$APKSIGNER_CMD verify \"$APK_PATH\"'"
    # Attempt to get more detailed output from apksigner
    $APKSIGNER_CMD verify -v "$APK_PATH"
    exit 1
fi
echo "APK Signing Check: PASSED"

echo "----------------------------------------"
echo "MyVRApp Release Build & Verification SUCCESSFUL!"
echo "APK ready at: $APK_PATH"
echo "----------------------------------------"

echo ""
echo "Starting Instrumentation Tests..."
# Ensure gradlew is executable. If this script is in MyVRApp, ./gradlew should work.
# For projects with multiple modules, you might need :app:connectedAndroidTest
./gradlew connectedAndroidTest
TEST_STATUS=$?

if [ $TEST_STATUS -ne 0 ]; then
    echo "Instrumentation Tests FAILED with status: $TEST_STATUS"
    echo "----------------------------------------"
    echo "MyVRApp Build & Verification SUCCESSFUL, but Automated Tests FAILED."
    echo "----------------------------------------"
    exit $TEST_STATUS # Exit with the test status
else
    echo "Instrumentation Tests PASSED."
    echo "----------------------------------------"
    echo "MyVRApp Release Build, Verification & Automated Tests ALL SUCCESSFUL!"
    echo "APK ready at: $APK_PATH" # Re-iterate APK path on full success
    echo "----------------------------------------"

    # <<< START NEW TAGGING LOGIC HERE >>>
    echo "----------------------------------------"
    echo "Starting Git tagging process..."
    read -p "Enter version tag (e.g., v1.0.0, leave empty to skip): " TAG_NAME

    if [ -z "$TAG_NAME" ]; then
        echo "Skipping tag creation."
    else
        # Basic validation: starts with 'v', then numbers and dots.
        if [[ "$TAG_NAME" =~ ^v[0-9]+(\.[0-9]+)*$ ]]; then
            echo "Attempting to create tag: $TAG_NAME"
            git tag "$TAG_NAME"
            TAG_CREATE_STATUS=$?

            if [ $TAG_CREATE_STATUS -ne 0 ]; then
                echo "Error: Failed to create Git tag '$TAG_NAME'. Please do it manually."
            else
                echo "Successfully tagged version $TAG_NAME."
                echo "Attempting to push tag $TAG_NAME to remote repository..."
                git push origin "$TAG_NAME"
                TAG_PUSH_STATUS=$?

                if [ $TAG_PUSH_STATUS -ne 0 ]; then
                    echo "Error: Failed to push Git tag '$TAG_NAME' to remote. Please do it manually."
                else
                    echo "Successfully pushed tag $TAG_NAME to remote repository."
                fi
            fi
        else
            echo "Error: Invalid tag format '$TAG_NAME'. Tag must start with 'v' followed by numbers and dots (e.g., v1.0.0)."
            echo "Skipping tag creation."
        fi
    fi
    echo "----------------------------------------"
    # <<< END NEW TAGGING LOGIC HERE >>>
fi

exit 0 # Success if tests passed (or rather, the script exits with TEST_STATUS if failed)
