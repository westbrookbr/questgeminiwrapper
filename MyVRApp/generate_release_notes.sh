#!/bin/bash

# Source the error handler
source MyVRApp/error_handler.sh || { echo "Failed to source error_handler.sh"; exit 1; }

OUTPUT_FILE="MyVRApp/RELEASE_NOTES.md"

# Get the latest tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null)

# If no tags exist, use the first commit as the starting point
if [ -z "$LATEST_TAG" ]; then
  START_COMMIT=$(git rev-list --max-parents=0 HEAD)
  echo "No tags found. Using first commit ($START_COMMIT) as a starting point."
else
  START_COMMIT="$LATEST_TAG"
  echo "Latest tag is $LATEST_TAG."
fi

# Fetch commit messages since the latest tag (or first commit)
# Format: - <Commit Subject> (<Commit Hash Short>)
COMMITS=$(git log --pretty=format:"- %s (%h)" $START_COMMIT..HEAD)

# Write header
echo "# Release Notes" > "$OUTPUT_FILE"

# Append formatted commit messages or no changes message
if [ -z "$COMMITS" ]; then
  echo "- No new changes since the last release." >> "$OUTPUT_FILE"
else
  echo "$COMMITS" >> "$OUTPUT_FILE"
fi

echo "Release notes generated at $OUTPUT_FILE"
