#!/bin/bash

# Script to analyze performance data for an Android application using ADB

PACKAGE_NAME=$1

if [ -z "$PACKAGE_NAME" ]; then
  echo "Usage: $0 <package_name>"
  exit 1
fi

echo "Performance Analysis for package: $PACKAGE_NAME"
echo "-------------------------------------------------"

# Check for connected devices
adb devices | grep -w "device" > /dev/null
if [ $? -ne 0 ]; then
  echo "Error: No ADB device found or device not authorized."
  echo "Please ensure a device is connected and USB debugging is enabled and authorized."
  exit 1
fi

echo "Collecting Graphics Performance Data (gfxinfo)..."
GFXINFO_OUTPUT=$(adb shell dumpsys gfxinfo "$PACKAGE_NAME")
if [ $? -ne 0 ]; then
  echo "Error: Failed to execute dumpsys gfxinfo for $PACKAGE_NAME."
  echo "Please ensure the package is installed and running, or that the device is properly connected."
  # Try to get general gfxinfo if package specific fails after a few seconds, maybe app was not running
  sleep 3
  GFXINFO_OUTPUT=$(adb shell dumpsys gfxinfo "$PACKAGE_NAME" framestats)
  if [ $? -ne 0 ]; then
    echo "Error: Still failed to execute dumpsys gfxinfo for $PACKAGE_NAME even with framestats."
    exit 1
  fi
fi

# Extracting relevant gfxinfo metrics (example: Janky frames, 90th percentile)
# Note: The output format of dumpsys gfxinfo can vary between Android versions.
# This is a basic parsing attempt. More robust parsing might be needed.

TOTAL_FRAMES=$(echo "$GFXINFO_OUTPUT" | grep -i "Total frames rendered" | awk '{print $4}')
JANKY_FRAMES=$(echo "$GFXINFO_OUTPUT" | grep -i "Janky frames" | awk '{print $3}')
PERCENTILE_90TH=$(echo "$GFXINFO_OUTPUT" | grep -i "90th percentile" | awk '{print $3}')
PERCENTILE_95TH=$(echo "$GFXINFO_OUTPUT" | grep -i "95th percentile" | awk '{print $3}')
PERCENTILE_99TH=$(echo "$GFXINFO_OUTPUT" | grep -i "99th percentile" | awk '{print $3}')

echo ""
echo "Graphics Metrics:"
if [ -n "$TOTAL_FRAMES" ]; then
  echo "  Total Frames Rendered: $TOTAL_FRAMES"
  echo "  Janky Frames: $JANKY_FRAMES"
  echo "  90th Percentile Frame Time: $PERCENTILE_90TH ms"
  echo "  95th Percentile Frame Time: $PERCENTILE_95TH ms"
  echo "  99th Percentile Frame Time: $PERCENTILE_99TH ms"
else
  echo "  Could not parse detailed frame statistics. Raw output might be:"
  # Attempt to find Profile data section for newer Android versions
  PROFILE_DATA=$(echo "$GFXINFO_OUTPUT" | awk '/Profile data in ms:/,/---PROFILEDATA---/')
  if [ -n "$PROFILE_DATA" ]; then
    echo "$PROFILE_DATA"
  else
    echo "  No standard frame statistics or Profile data found. Consider checking raw dumpsys output."
  fi
fi


echo ""
echo "Collecting Memory Usage Data (meminfo)..."
MEMINFO_OUTPUT=$(adb shell dumpsys meminfo "$PACKAGE_NAME" -d) # -d for detailed info
if [ $? -ne 0 ]; then
  echo "Error: Failed to execute dumpsys meminfo for $PACKAGE_NAME."
  echo "Please ensure the package is installed, or that the device is properly connected."
  exit 1
fi

# Extracting relevant meminfo metrics (example: PSS Total, Java Heap, Native Heap)
PSS_TOTAL=$(echo "$MEMINFO_OUTPUT" | grep -i "TOTAL" | grep -E "TOTAL\s+[0-9]+" | awk '{print $2}')
JAVA_HEAP=$(echo "$MEMINFO_OUTPUT" | grep "Java Heap:" | awk '{print $3}') # KB
NATIVE_HEAP=$(echo "$MEMINFO_OUTPUT" | grep "Native Heap:" | awk '{print $3}') # KB
CODE_PSS=$(echo "$MEMINFO_OUTPUT" | grep "Code:" | grep -E "Code:\s+[0-9]+" | awk '{print $2}') # KB
STACK_PSS=$(echo "$MEMINFO_OUTPUT" | grep "Stack:" | awk '{print $2}') # KB
GRAPHICS_PSS=$(echo "$MEMINFO_OUTPUT" | grep "Graphics:" | awk '{print $2}') # KB
PRIVATE_OTHER_PSS=$(echo "$MEMINFO_OUTPUT" | grep "Private Other:" | awk '{print $3}') # KB
SYSTEM_PSS=$(echo "$MEMINFO_OUTPUT" | grep "System:" | awk '{print $2}') # KB

echo ""
echo "Memory Metrics (in KB):"
if [ -n "$PSS_TOTAL" ]; then
  echo "  PSS Total: $PSS_TOTAL"
  echo "  Java Heap (PSS): $JAVA_HEAP"
  echo "  Native Heap (PSS): $NATIVE_HEAP"
  echo "  Code (PSS): $CODE_PSS"
  echo "  Stack (PSS): $STACK_PSS"
  echo "  Graphics (PSS): $GRAPHICS_PSS"
  echo "  Private Other (PSS): $PRIVATE_OTHER_PSS"
  echo "  System (PSS): $SYSTEM_PSS"

  echo ""
  echo "Objects:"
  VIEWS=$(echo "$MEMINFO_OUTPUT" | grep "Views:" | awk '{print $2}')
  ACTIVITIES=$(echo "$MEMINFO_OUTPUT" | grep "Activities:" | awk '{print $2}')
  echo "  Views: $VIEWS"
  echo "  Activities: $ACTIVITIES"
else
  echo "  Could not parse detailed PSS values. Raw output might be more informative."
  echo "$MEMINFO_OUTPUT" # Print raw if parsing fails
fi

echo ""
echo "-------------------------------------------------"
echo "Performance analysis complete."
echo "Note: gfxinfo data is most accurate after consistent UI rendering (e.g., after animations or gameplay)."
echo "Note: meminfo PSS values provide a good estimate of proportional memory usage."

exit 0
