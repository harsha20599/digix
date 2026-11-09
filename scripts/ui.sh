#!/bin/zsh
# Small helpers for driving the app over ADB during screen checks. Usage: source scripts/ui.sh <serial>
SERIAL="$1"
REVIEW=/Users/harsha20599/Dialect/.impeccable/review
mkdir -p "$REVIEW"

shot() {
  adb -s "$SERIAL" exec-out screencap -p > "$REVIEW/$1.png"
  sips -Z 1100 "$REVIEW/$1.png" >/dev/null
}

# Taps the first element whose text or description matches exactly.
tapt() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1
  local line nums
  line=$(adb -s "$SERIAL" shell cat /sdcard/d.xml | tr '>' '\n' | grep -E "(text|content-desc)=\"$1\"" | head -1)
  nums=($(echo "$line" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+'))
  if [ ${#nums[@]} -eq 4 ]; then
    adb -s "$SERIAL" shell input tap $(( (nums[1]+nums[3])/2 )) $(( (nums[2]+nums[4])/2 ))
  else
    echo "NOT FOUND: $1"
  fi
}

texts() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/d.xml >/dev/null 2>&1
  adb -s "$SERIAL" shell cat /sdcard/d.xml | grep -oE '(text|content-desc)="[^"]+"' | cut -c1-90
}
