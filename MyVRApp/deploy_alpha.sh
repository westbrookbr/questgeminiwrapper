#!/bin/bash

# --- Logging Function ---
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# --- Usage Definition ---
if [ "$#" -lt 2 ]; then
    echo "Usage: ./deploy_alpha.sh <path_to_apk> <target_type> [target_destination/credentials...]"
    echo "Target types: local, meta_quest, firebase"
    exit 1
fi

# --- Arguments ---
APK_PATH="$1"
TARGET_TYPE="$2"
# $3 and onwards will be handled by specific targets

log "Starting deployment process for APK: $APK_PATH to target type: $TARGET_TYPE"

# --- Verify APK Path ---
if [ ! -f "$APK_PATH" ]; then
    log "ERROR: APK file not found at '$APK_PATH'."
    exit 1
fi
log "APK found at '$APK_PATH'."

# --- Target Handling ---
case "$TARGET_TYPE" in
    local)
        TARGET_DESTINATION="$3"
        log "Selected target: local"
        if [ -z "$TARGET_DESTINATION" ]; then
            log "ERROR: Target destination directory not provided for 'local' deployment."
            exit 1
        fi
        if [ ! -d "$TARGET_DESTINATION" ]; then
            log "ERROR: Target destination '$TARGET_DESTINATION' is not a directory or does not exist."
            exit 1
        fi

        log "Copying APK to $TARGET_DESTINATION..."
        cp "$APK_PATH" "$TARGET_DESTINATION/"
        CP_STATUS=$?
        if [ $CP_STATUS -ne 0 ]; then
            log "ERROR: Failed to copy APK to $TARGET_DESTINATION."
            exit 1
        else
            log "Successfully copied APK to $TARGET_DESTINATION/$(basename "$APK_PATH")"
        fi
        ;;

    meta_quest)
        APP_ID="$3"
        APP_SECRET_ENV="OVR_APP_SECRET"
        ACCESS_TOKEN_ENV="OVR_ACCESS_TOKEN"
        CHANNEL="ALPHA"
        OVR_PLATFORM_UTIL_PATH="${OVR_PLATFORM_UTIL_PATH:-ovr-platform-util}" # Use env var or default

        log "Selected target: meta_quest"
        log "Using OVR Platform Util: $OVR_PLATFORM_UTIL_PATH"
        log "Target App ID: $APP_ID"
        log "Target Channel: $CHANNEL"

        if ! command -v "$OVR_PLATFORM_UTIL_PATH" &> /dev/null; then
            log "ERROR: '$OVR_PLATFORM_UTIL_PATH' command not found or not executable. Please ensure it's installed and in PATH, or OVR_PLATFORM_UTIL_PATH is set correctly."
            exit 1
        fi

        if [ -z "$APP_ID" ]; then
            log "ERROR: Meta Quest App ID not provided as the third argument."
            exit 1
        fi

        MISSING_CREDENTIALS=0
        if [ -z "${!APP_SECRET_ENV}" ]; then # Indirect expansion to check env var
            log "ERROR: Environment variable $APP_SECRET_ENV is not set. This is required for Meta Quest uploads."
            MISSING_CREDENTIALS=1
        fi
        if [ -z "${!ACCESS_TOKEN_ENV}" ]; then
            log "ERROR: Environment variable $ACCESS_TOKEN_ENV is not set. This is required for Meta Quest uploads."
            MISSING_CREDENTIALS=1
        fi

        if [ $MISSING_CREDENTIALS -ne 0 ]; then
            log "ERROR: Missing one or more credentials for Meta Quest deployment. Please set the required environment variables."
            exit 1
        fi

        log "All checks for Meta Quest passed. App ID and credentials variables are present."
        # Placeholder: Log the command instead of executing
        log "Executing (dry run): $OVR_PLATFORM_UTIL_PATH upload-quest-build --app_id \"$APP_ID\" --app_secret \"\${$APP_SECRET_ENV}\" --token \"\${$ACCESS_TOKEN_ENV}\" --apk \"$APK_PATH\" --channel \"$CHANNEL\""
        log "NOTE: This is a dry run. Actual upload command for Meta Quest is logged above. Implement direct execution when ready."
        ;;

    firebase)
        FIREBASE_CLI_PATH="${FIREBASE_CLI_PATH:-firebase}" # Use env var or default
        FIREBASE_APP_ID_ENV="FIREBASE_APP_ID"
        FIREBASE_TOKEN_ENV="FIREBASE_TOKEN" # Often used in CI
        TESTERS_GROUP_ALIAS="alpha-testers"

        log "Selected target: firebase"
        log "Using Firebase CLI: $FIREBASE_CLI_PATH"
        log "Target Firebase App ID Env Var: $FIREBASE_APP_ID_ENV (Value: ${!FIREBASE_APP_ID_ENV})"
        log "Testers Group: $TESTERS_GROUP_ALIAS"


        if ! command -v "$FIREBASE_CLI_PATH" &> /dev/null; then
            log "ERROR: '$FIREBASE_CLI_PATH' command not found or not executable. Please ensure it's installed and in PATH, or FIREBASE_CLI_PATH is set correctly."
            exit 1
        fi

        MISSING_FIREBASE_CONFIG=0
        if [ -z "${!FIREBASE_APP_ID_ENV}" ]; then
            log "ERROR: Environment variable $FIREBASE_APP_ID_ENV is not set. This is required for Firebase App Distribution."
            MISSING_FIREBASE_CONFIG=1
        fi

        # Token is more for CI, might not be set locally if user is logged into Firebase CLI
        if [ -z "${!FIREBASE_TOKEN_ENV}" ]; then
            log "INFO: Environment variable $FIREBASE_TOKEN_ENV is not set. This might be required for CI environments or if not logged into Firebase CLI."
        fi

        if [ $MISSING_FIREBASE_CONFIG -ne 0 ]; then
            log "ERROR: Missing Firebase configuration. Please set the required environment variables."
            exit 1
        fi

        log "All checks for Firebase passed. Required configurations appear to be present."
        # Placeholder: Log the command instead of executing
        log "Executing (dry run): $FIREBASE_CLI_PATH appdistribution:distribute \"$APK_PATH\" --app \"\${$FIREBASE_APP_ID_ENV}\" --release-notes \"Automatic alpha release from deploy_alpha.sh\" --groups \"$TESTERS_GROUP_ALIAS\" --token \"\${$FIREBASE_TOKEN_ENV}\""
        log "NOTE: This is a dry run for Firebase. Actual command logged above. Ensure you are logged in with 'firebase login' if not using a token."
        ;;

    *)
        log "ERROR: Invalid target_type '$TARGET_TYPE'. Supported types are: local, meta_quest, firebase."
        exit 1
        ;;
esac

log "Deployment script finished."
exit 0
