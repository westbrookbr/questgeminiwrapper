#!/bin/bash

# Function to handle errors
handle_error() {
    local script_name="$1"
    local line_number="$2"
    local error_message="$3"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')

    echo "[$timestamp] ERROR in $script_name at line $line_number: $error_message" >&2
    exit 1
}
