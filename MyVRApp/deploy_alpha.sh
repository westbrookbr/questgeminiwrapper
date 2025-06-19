#!/bin/bash

# Source the error handler
source MyVRApp/error_handler.sh || { echo "Failed to source error_handler.sh"; exit 1; }

# --- Logging Function ---
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# --- Usage Definition ---
if [ "$#" -lt 2 ]; then
    # For usage errors, a direct echo might be cleaner than the full handle_error format.
    # However, to maintain consistency and ensure all exits are handled, we can use it.
    # Alternatively, keep the echo and exit, or create a specific usage_error function.
    # For now, let's use handle_error for consistency.
    USAGE_MSG="Usage: ./deploy_alpha.sh <path_to_apk> <target_type> [target_destination/credentials...]. Target types: local, meta_quest, firebase"
    handle_error "$0" "$LINENO" "$USAGE_MSG"
fi

# --- Arguments ---
APK_PATH="$1"
TARGET_TYPE="$2"
# $3 and onwards will be handled by specific targets

log "Starting deployment process for APK: $APK_PATH to target type: $TARGET_TYPE"

# --- Verify APK Path ---
if [ ! -f "$APK_PATH" ]; then
    handle_error "$0" "$LINENO" "APK file not found at '$APK_PATH'."
fi
log "APK found at '$APK_PATH'."

# --- Target Handling ---
case "$TARGET_TYPE" in
    local)
        TARGET_DESTINATION="$3"
        log "Selected target: local"
        if [ -z "$TARGET_DESTINATION" ]; then
            handle_error "$0" "$LINENO" "Target destination directory not provided for 'local' deployment."
        fi
        if [ ! -d "$TARGET_DESTINATION" ]; then
            handle_error "$0" "$LINENO" "Target destination '$TARGET_DESTINATION' is not a directory or does not exist."
        fi

        log "Copying APK to $TARGET_DESTINATION..."
        cp "$APK_PATH" "$TARGET_DESTINATION/"
        CP_STATUS=$?
        if [ $CP_STATUS -ne 0 ]; then
            handle_error "$0" "$LINENO" "Failed to copy APK to $TARGET_DESTINATION. cp exit status: $CP_STATUS"
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
            handle_error "$0" "$LINENO" "'$OVR_PLATFORM_UTIL_PATH' command not found or not executable. Please ensure it's installed and in PATH, or OVR_PLATFORM_UTIL_PATH is set correctly."
        fi

        if [ -z "$APP_ID" ]; then
            handle_error "$0" "$LINENO" "Meta Quest App ID not provided as the third argument."
        fi

        MISSING_CREDENTIALS_MSG=""
        if [ -z "${!APP_SECRET_ENV}" ]; then # Indirect expansion to check env var
            MISSING_CREDENTIALS_MSG+="Environment variable $APP_SECRET_ENV is not set. "
        fi
        if [ -z "${!ACCESS_TOKEN_ENV}" ]; then
            MISSING_CREDENTIALS_MSG+="Environment variable $ACCESS_TOKEN_ENV is not set. "
        fi

        if [ -n "$MISSING_CREDENTIALS_MSG" ]; then
            handle_error "$0" "$LINENO" "Missing credentials for Meta Quest: ${MISSING_CREDENTIALS_MSG}This is required for Meta Quest uploads."
        fi

        log "All checks for Meta Quest passed. App ID and credentials variables are present."
        log "Executing: $OVR_PLATFORM_UTIL_PATH upload-quest-build --app_id \"$APP_ID\" --app_secret \"${!APP_SECRET_ENV}\" --token \"${!ACCESS_TOKEN_ENV}\" --apk \"$APK_PATH\" --channel \"$CHANNEL\""

        "$OVR_PLATFORM_UTIL_PATH" upload-quest-build --app_id "$APP_ID" --app_secret "${!APP_SECRET_ENV}" --token "${!ACCESS_TOKEN_ENV}" --apk "$APK_PATH" --channel "$CHANNEL"
        UPLOAD_STATUS=$?

        if [ $UPLOAD_STATUS -ne 0 ]; then
            handle_error "$0" "$LINENO" "ovr-platform-util command failed with exit code $UPLOAD_STATUS."
        else
            log "Successfully uploaded build to Meta Quest via ovr-platform-util."
        fi
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
            handle_error "$0" "$LINENO" "'$FIREBASE_CLI_PATH' command not found or not executable. Please ensure it's installed and in PATH, or FIREBASE_CLI_PATH is set correctly."
        fi

        MISSING_FIREBASE_CONFIG_MSG=""
        if [ -z "${!FIREBASE_APP_ID_ENV}" ]; then
            MISSING_FIREBASE_CONFIG_MSG+="Environment variable $FIREBASE_APP_ID_ENV is not set. "
        fi

        # Token is more for CI, might not be set locally if user is logged into Firebase CLI
        # This is an INFO message, so it doesn't need to use handle_error
        if [ -z "${!FIREBASE_TOKEN_ENV}" ]; then
            log "INFO: Environment variable $FIREBASE_TOKEN_ENV is not set. This might be required for CI environments or if not logged into Firebase CLI."
        fi

        if [ -n "$MISSING_FIREBASE_CONFIG_MSG" ]; then
            handle_error "$0" "$LINENO" "Missing Firebase configuration: ${MISSING_FIREBASE_CONFIG_MSG}This is required for Firebase App Distribution."
        fi

        log "All checks for Firebase passed. Required configurations appear to be present."

        RELEASE_NOTES_CONTENT="Automatic alpha release from deploy_alpha.sh"
        if [ -f "RELEASE_NOTES.md" ]; then
            log "Found RELEASE_NOTES.md, using its content for release notes."
            RELEASE_NOTES_CONTENT=$(cat "RELEASE_NOTES.md")
        else
            log "RELEASE_NOTES.md not found, using default release notes."
        fi

        FIREBASE_CMD_ARGS=("$FIREBASE_CLI_PATH" appdistribution:distribute "$APK_PATH" --app "${!FIREBASE_APP_ID_ENV}" --release-notes "$RELEASE_NOTES_CONTENT" --groups "$TESTERS_GROUP_ALIAS")

        if [ -n "${!FIREBASE_TOKEN_ENV}" ]; then
            log "Firebase token is set, adding it to the command."
            FIREBASE_CMD_ARGS+=("--token" "${!FIREBASE_TOKEN_ENV}")
        else
            log "Firebase token is not set. Ensure you are logged in with 'firebase login' if not running in CI."
        fi

        log "Executing: ${FIREBASE_CMD_ARGS[*]}"
        "${FIREBASE_CMD_ARGS[@]}"
        UPLOAD_STATUS=$?

        if [ $UPLOAD_STATUS -ne 0 ]; then
            handle_error "$0" "$LINENO" "Firebase CLI command failed with exit code $UPLOAD_STATUS."
        else
            log "Successfully distributed APK via Firebase App Distribution."
        fi
        ;;

    *)
        handle_error "$0" "$LINENO" "Invalid target_type '$TARGET_TYPE'. Supported types are: local, meta_quest, firebase."
        ;;
esac

log "Deployment script finished."
exit 0
