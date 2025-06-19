#!/bin/bash

# Source the error handler
source MyVRApp/error_handler.sh || { echo "Failed to source error_handler.sh"; exit 1; }

# Script to build a release APK for MyVRApp, verify it, and prepare for testing.

echo "Starting MyVRApp release build process..."

# 1. Execute Gradle build
# Ensure this script is run from the MyVRApp directory or that gradlew is accessible.
./gradlew assembleRelease
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    handle_error "$0" "$LINENO" "Gradle build failed with status: $BUILD_STATUS"
fi

echo "Gradle build successful."

# 2. Define APK path
APK_PATH="app/build/outputs/apk/release/app-release.apk"
echo "Expected APK path: $APK_PATH"

# 3. Verify APK
# 3a. Check existence
if [ ! -f "$APK_PATH" ]; then
    handle_error "$0" "$LINENO" "Build Verification FAILED: APK not found at $APK_PATH"
fi
echo "APK Existence Check: PASSED"

# 3b. Check non-zero size
if [ ! -s "$APK_PATH" ]; then
    handle_error "$0" "$LINENO" "Build Verification FAILED: APK is zero size."
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
    # Attempt to get more detailed output from apksigner before calling handle_error
    echo "Command used: '$APKSIGNER_CMD verify \"$APK_PATH\"'"
    $APKSIGNER_CMD verify -v "$APK_PATH" >&2 # Send verbose output to stderr
    handle_error "$0" "$LINENO" "Build Verification FAILED: APK signing verification failed for $APK_PATH."
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
    # Not using handle_error here as we want to exit with the specific TEST_STATUS
    # and the script has a specific message for this case.
    echo "Instrumentation Tests FAILED with status: $TEST_STATUS" >&2
    echo "----------------------------------------" >&2
    echo "MyVRApp Build & Verification SUCCESSFUL, but Automated Tests FAILED." >&2
    echo "----------------------------------------" >&2
    exit $TEST_STATUS
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
        # Strict Semantic Versioning Check: vX.Y.Z
        if [[ ! "$TAG_NAME" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            handle_error "$0" "$LINENO" "Invalid tag format '$TAG_NAME'. Tag must be vX.Y.Z (e.g., v1.0.0)."
        fi
        echo "Tag format validated: $TAG_NAME"

        # Check for existing local tag
        if git tag -l "$TAG_NAME" | grep -q "$TAG_NAME"; then
            handle_error "$0" "$LINENO" "Tag '$TAG_NAME' already exists locally."
        fi
        echo "Tag '$TAG_NAME' does not exist locally."

        # Check for existing remote tag
        # Note: git ls-remote exits with 0 if remote tag exists, 2 if it doesn't (or other error)
        # We are interested if it *finds* the tag.
        if git ls-remote --tags origin "refs/tags/$TAG_NAME" | grep -q "refs/tags/$TAG_NAME"; then
            handle_error "$0" "$LINENO" "Tag '$TAG_NAME' already exists remotely on origin."
        fi
        echo "Tag '$TAG_NAME' does not exist remotely on origin."

        echo "Attempting to create tag: $TAG_NAME"
        git tag "$TAG_NAME"
        TAG_CREATE_STATUS=$?

        if [ $TAG_CREATE_STATUS -ne 0 ]; then
            handle_error "$0" "$LINENO" "Failed to create Git tag '$TAG_NAME'. 'git tag' command failed with status $TAG_CREATE_STATUS."
        fi
        echo "Successfully tagged version $TAG_NAME locally."

        echo "Attempting to push tag $TAG_NAME to remote repository origin..."
        git push origin "$TAG_NAME"
        TAG_PUSH_STATUS=$?

        if [ $TAG_PUSH_STATUS -ne 0 ]; then
            # It might be useful to try and delete the local tag if push fails, to allow a retry of the script.
            # However, for now, just report the error.
            handle_error "$0" "$LINENO" "Failed to push Git tag '$TAG_NAME' to remote 'origin'. 'git push' command failed with status $TAG_PUSH_STATUS."
        fi
        echo "Successfully pushed tag $TAG_NAME to remote repository origin."
    fi
    echo "----------------------------------------"
    # <<< END NEW TAGGING LOGIC HERE >>>
fi

exit 0 # Success if tests passed
