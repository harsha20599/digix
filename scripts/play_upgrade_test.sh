#!/bin/bash
# Destructive test setup. Use a disposable emulator and two debug-signed APKs.
set -euo pipefail

serial="${1:?Pass a disposable emulator serial}"
legacy_apk="${2:?Pass an APK of the original Play release}"
updated_apk="${3:?Pass the new Play staging APK}"
package=com.littyl.digix.play
role=android.app.role.CALL_REDIRECTION
device() { adb -s "$serial" shell "$@"; }

case "$serial" in emulator-*) ;; *) echo 'Refusing a physical device' >&2; exit 2;; esac
test "$(device getprop ro.kernel.qemu | tr -d '\r')" = 1

adb -s "$serial" uninstall "$package" >/dev/null 2>&1 || true
adb -s "$serial" install "$legacy_apk"
device "run-as $package mkdir -p shared_prefs"
device "run-as $package sh -c 'cat > shared_prefs/shortcuts.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="BATTERY">0122</string>
    <string name="RINGER">0123</string>
    <boolean name="enabled" value="false" />
</map>
XML
device cmd role add-role-holder "$role" "$package" 0

# In-place installation is the point of the test. Keep the Play package's data.
adb -s "$serial" install -r "$updated_apk"
device am start -W -n "$package/com.littyl.digix.play.MainActivity"
test "$(device cmd role get-role-holders "$role" | tr -d '\r')" = "$package"
echo 'PASS  upgraded in place and retained call redirection'

check_migration() {
    device "run-as $package cat shared_prefs/digix.xml" | python3 -c '
import json, sys, xml.etree.ElementTree as ET
prefs = {item.get("name"): item for item in ET.fromstring(sys.stdin.read())}
codes = {item["a"]: item["c"] for item in json.loads(prefs["codes"].text)}
assert codes == {"BATTERY": "0122", "PLAY_PAUSE": "0104", "NEXT_TRACK": "0105", "VIBRATE_MODE": "0123"}, codes
assert prefs["enabled"].get("value") == "false"
assert prefs["spoken_replies"].get("value") == "false"
assert prefs["first_run_done"].get("value") == "true"
'
}
check_migration
device am force-stop "$package"
device am start -W -n "$package/com.littyl.digix.play.MainActivity" >/dev/null
check_migration
echo 'PASS  custom and default codes, paused state and silent replies survive restart'

redirected() { device dumpsys telecom | awk '/REDIRECTION_COMPLETED/ {line=$0} END {print line}'; }
previous=$(redirected)
device am start -a android.intent.action.CALL -d tel:0122 >/dev/null
for attempt in $(seq 1 30); do
    current=$(redirected)
    if [[ "$current" != "$previous" && "$current" == *CRA.cC* ]]; then
        echo 'PASS  migrated paused code was canceled before reaching the network'
        exit 0
    fi
    sleep 1
done

echo 'FAIL  no cancellation recorded for the migrated code' >&2
exit 1
