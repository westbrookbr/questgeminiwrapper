#!/bin/bash

# Script to build the MyVRApp Android application with a Gemini API Key provided as an argument.
#
# Usage:
# ./build_with_api_key.sh YOUR_GEMINI_API_KEY
#
# The script will set the GEMINI_API_KEY environment variable and then trigger a debug build.

API_KEY=$1

if [ -z "$API_KEY" ]; then
  echo "Error: No API key provided."
  echo "Usage: ./build_with_api_key.sh YOUR_GEMINI_API_KEY"
  exit 1
fi

echo "Building with GEMINI_API_KEY set..."

# Export the API key as an environment variable
export GEMINI_API_KEY="$API_KEY"

# Run the Gradle build (using debug build as an example)
# Ensure you are in the MyVRApp directory when running this if the script is located elsewhere.
# If the script is inside MyVRApp, ./gradlew will work directly.
./gradlew assembleDebug

# Unset the environment variable after build (optional, good practice)
unset GEMINI_API_KEY

echo "Build finished."
