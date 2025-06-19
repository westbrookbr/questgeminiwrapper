#!/bin/bash

# Source the error handler
source MyVRApp/error_handler.sh || { echo "Failed to source error_handler.sh"; exit 1; }

# Script to build the MyVRApp Android application with a Gemini API Key provided as an argument.
#
# Usage:
# ./build_with_api_key.sh YOUR_GEMINI_API_KEY
#
# The script will set the GEMINI_API_KEY environment variable and then trigger a debug build.

API_KEY=$1

if [ -z "$API_KEY" ]; then
  handle_error "$0" "$LINENO" "No API key provided. Usage: ./build_with_api_key.sh YOUR_GEMINI_API_KEY"
fi

echo "Building with GEMINI_API_KEY set..."

# Export the API key as an environment variable
export GEMINI_API_KEY="$API_KEY"

# Run the Gradle build (using debug build as an example)
# Ensure you are in the MyVRApp directory when running this if the script is located elsewhere.
# If the script is inside MyVRApp, ./gradlew will work directly.
./gradlew assembleDebug
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    # Unset the environment variable even if build fails
    unset GEMINI_API_KEY
    handle_error "$0" "$LINENO" "Gradle debug build failed with status: $BUILD_STATUS"
fi

# Unset the environment variable after successful build (optional, good practice)
unset GEMINI_API_KEY

echo "Build finished."
