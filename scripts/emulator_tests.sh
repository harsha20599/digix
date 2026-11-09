#!/bin/zsh
# End-to-end checks of Digix's call handling on an emulator. Its modem is simulated, so no real
# call can leave the machine. Usage: scripts/emulator_tests.sh <emulator-serial>
# It refuses to run against anything that is not an emulator. Checks read Android's own records and
# poll with generous limits, so a slow machine costs time rather than false results, and several
# emulators can run the suite at once.
E="${1:-emulator-5554}"
case "$E" in emulator-*) ;; *) print "Refusing: $E is not an emulator"; exit 2;; esac
[ "$(adb -s "$E" shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ] || { print "Refusing: not an emulator"; exit 2; }

LIMIT=25
D() { adb -s "$E" shell "$@"; }
PASS=0; FAIL=0
ok() { PASS=$((PASS+1)); print -- "PASS  $1"; }
no() { FAIL=$((FAIL+1)); print -- "FAIL  $1  [$2]"; }
callstate() { D dumpsys telephony.registry | grep -m1 -oE 'mCallState=[0-9]' | cut -d= -f2; }
awake() { D input keyevent KEYCODE_WAKEUP; D wm dismiss-keyguard; sleep .4; }
now_ms() { print "$(D date +%s | tr -d '\r')000"; }
home() { D input keyevent KEYCODE_HOME; sleep .5; }
# Waits for any reply call to finish first, so one test's call can't leak into the next.
stop_app() {
  local i
  for i in {1..$LIMIT}; do
    D dumpsys telecom | grep -E '^\s+\[?Call id=TC' | grep -q 'tpac=ComponentInfo{com.littyl.digix' || break
    sleep 1
  done
  D am force-stop com.littyl.digix
  sleep 1
}
prefs() { D "run-as com.littyl.digix cat shared_prefs/digix.xml" 2>/dev/null; }
_unused_cancel() { D dumpsys telecom | grep -E 'REQUEST_DISCONNECT \(Canceled from Call Redirection' | tail -1 | cut -c1-40; }
set_pref() { D "run-as com.littyl.digix sed -i 's/$1/$2/' shared_prefs/digix.xml"; }

# Ends any call in progress. With no call, the end-call key would put the device to sleep.
end_calls() {
  local i
  for i in {1..$LIMIT}; do
    [ "$(callstate)" = "0" ] && return 0
    D input keyevent KEYCODE_ENDCALL
    sleep 1
  done
  return 1
}

dial() { [ "$LOCKED" = "1" ] || awake; D am start -a android.intent.action.CALL -d "tel:$1" >/dev/null; }

# Proves a code never became a phone-network call: Android's redirection record shows Digix took
# it (cancelled or redirected, never passed through) and no network call came up.
newest_handled() { D dumpsys telecom | grep -E 'REDIRECTION_COMPLETED' | tail -1 | sed -E 's/^ *//' | cut -c1-60; }
expect_stopped() { # label, previous newest record
  local i current
  for i in {1..$LIMIT}; do
    [ "$(callstate)" = "2" ] && { no "$1" "a network call came up"; end_calls; return; }
    current=$(newest_handled)
    if [ -n "$current" ] && [ "$current" != "$2" ]; then
      [[ "$current" == *CRA.pCU* ]] && { no "$1" "passed through as a real number"; end_calls; return; }
      sleep 1
      [ "$(callstate)" = "2" ] && no "$1" "a network call came up" || ok "$1"
      return
    fi
    sleep 1
  done
  no "$1" "no record, state=$(callstate)"
  end_calls
}

expect_call() { # label
  local i
  for i in {1..$LIMIT}; do
    [ "$(callstate)" = "2" ] && { ok "$1"; return; }
    sleep 1
  done
  no "$1" "no call appeared"
}

# Waits for a log entry for this code written at or after the given time, and prints it.
entry_since() { # code, since_ms
  local i x
  for i in {1..$LIMIT}; do
    x=$(prefs | python3 -c "
import sys,re,json,html
m=re.search(r'name=\"log\">(.*?)</string>',sys.stdin.read(),re.S)
if m:
    hit=[e for e in json.loads(html.unescape(m.group(1))) if e['c']=='$1' and e['t']>=$2]
    if hit: print(hit[0]['s']+'|'+hit[0]['m'])
")
    [ -n "$x" ] && { print -- "$x"; return; }
    sleep 1
  done
}

until_true() { # label, command string
  local i
  for i in {1..$LIMIT}; do
    eval "$2" && { ok "$1"; return; }
    sleep 1
  done
  no "$1" "not within ${LIMIT}s"
}
service_foreground() { D dumpsys activity services com.littyl.digix | grep -q 'isForeground=true'; }
service_gone() { [ "$(D dumpsys activity services com.littyl.digix | grep -c 'ServiceRecord{.* com.littyl.digix/')" = "0" ]; }
alarm_playing() { [ "$(D dumpsys audio | grep -E 'state:started' | grep -c USAGE_ALARM)" -ge 1 ]; }
alarm_stopped() { ! alarm_playing; }

SDK=$(D getprop ro.build.version.sdk | tr -d '\r')
print -- "--- setup (Android API $SDK)"
awake
D pm clear com.littyl.digix >/dev/null
D cmd role add-role-holder android.app.role.CALL_REDIRECTION com.littyl.digix 0
[ "$SDK" -ge 33 ] && D pm grant com.littyl.digix android.permission.POST_NOTIFICATIONS
D pm grant com.littyl.digix android.permission.READ_PHONE_STATE
D am start -n com.littyl.digix/.ui.MainActivity >/dev/null
for i in {1..60}; do prefs | grep -q first_run_done && break; sleep 1; done
prefs | grep -q first_run_done || { print "setup never finished"; exit 3; }
home
if [ "$SDK" -ge 30 ]; then
  held=$(D cmd role get-role-holders android.app.role.CALL_REDIRECTION | tr -d '\r')
  [ "$held" = "com.littyl.digix" ] && ok "role granted" || no "role granted" "$held"
fi

print -- "--- 1. a normal number passes through"
dial 5551234
expect_call "normal call was placed"
end_calls
[ "$(prefs | grep -c 5551234)" = "0" ] && ok "normal number not stored anywhere" || no "normal number not stored" "found"

print -- "--- 2. a code is caught and no call starts"
c=$(newest_handled); t=$(now_ms)
dial 0103
expect_stopped "code 0103 cancelled by Android, no call" "$c"
x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "battery ran: ${x#*|}" || no "battery ran" "$x"

print -- "--- 3. the app is woken from nothing"
home; stop_app
[ -z "$(D pidof com.littyl.digix | tr -d '\r')" ] && ok "process is dead before dialing" || no "process dead" "alive"
c=$(newest_handled); t=$(now_ms)
dial 0104
expect_stopped "code after force-stop cancelled, no call" "$c"
x=$(entry_since 0104 $t); [[ "$x" == DONE* ]] && ok "ran after force-stop: ${x#*|}" || no "ran after force-stop" "$x"

print -- "--- 4. Find my phone holds, and dialing again stops it"
home; stop_app
c=$(newest_handled); t=$(now_ms)
dial 0102
expect_stopped "code cancelled, no call" "$c"
until_true "foreground service alive while ringing" service_foreground
until_true "alarm audio is playing" alarm_playing
x=$(entry_since 0102 $t); [[ "$x" == DONE* ]] && ok "find phone reported: ${x#*|}" || no "find phone reported" "$x"
c=$(newest_handled)
dial 0102
expect_stopped "second dial cancelled, no call" "$c"
until_true "service released after dialing again" service_gone
until_true "alarm audio stopped" alarm_stopped

print -- "--- 5. master switch off: code still stopped, nothing runs"
stop_app
set_pref 'name=\"enabled\" value=\"true\"' 'name=\"enabled\" value=\"false\"'
c=$(newest_handled); t=$(now_ms)
dial 0103
expect_stopped "off: code cancelled, no call" "$c"
x=$(entry_since 0103 $t); [[ "$x" == STOPPED_OFF* ]] && ok "off: recorded as stopped: ${x#*|}" || no "off: recorded as stopped" "$x"
stop_app
set_pref 'name=\"enabled\" value=\"false\"' 'name=\"enabled\" value=\"true\"'

print -- "--- 6. locked phone"
D locksettings set-pin 1379 >/dev/null 2>&1
D input keyevent KEYCODE_SLEEP; sleep 2
LOCKED=1
c=$(newest_handled); t=$(now_ms)
dial 0104
expect_stopped "locked: code cancelled, no call" "$c"
x=$(entry_since 0104 $t); [[ "$x" == DONE* ]] && ok "locked: action ran" || no "locked: action ran" "$x"
dial 5551234
expect_call "locked: normal call passes through"
end_calls
D locksettings clear --old 1379 >/dev/null 2>&1
LOCKED=0
awake

print -- "--- 7. a removed code is an ordinary number again"
stop_app
set_pref '0104' '0199'
t=$(now_ms)
dial 0104
sleep 6
[ -z "$(prefs | python3 -c "
import sys,re,json,html
m=re.search(r'name=\"log\">(.*?)</string>',sys.stdin.read(),re.S)
print([e for e in json.loads(html.unescape(m.group(1))) if e['c']=='0104' and e['t']>=$t] if m else '')" | grep 0104)" ] && ok "unknown 0104 was not treated as a code" || no "unknown 0104 not a code" "logged"
end_calls || no "previous call ended" "state=$(callstate)"

print -- "--- 8. without the battery exemption"
D dumpsys deviceidle whitelist -com.littyl.digix >/dev/null
stop_app
c=$(newest_handled); t=$(now_ms)
dial 0102
expect_stopped "code cancelled, no call" "$c"
x=$(entry_since 0102 $t)
# The app's target has no background-start limit, so it simply runs.
[[ "$x" == DONE* ]] && ok "runs without the exemption: ${x#*|}" || no "runs without the exemption" "$x"
until_true "alarm audio is playing" alarm_playing
dial 0102; until_true "stops again" service_gone

print -- "--- 9. the spoken reply call"
stop_app
reply_active() { D dumpsys telecom | grep -E '^\s+\[?Call id=TC' | grep -q 'tpac=ComponentInfo{com.littyl.digix'; }
reply_gone() { ! reply_active; }
log_rows() { D content query --uri content://call_log/calls 2>/dev/null | grep -c 'Row:'; }
rows_before=$(log_rows)
c=$(newest_handled); t=$(now_ms)
dial 0103
until_true "reply call connects" reply_active
[ "$(callstate)" = "0" ] && ok "phone network stays idle during the reply" || no "phone network stays idle" "state=$(callstate)"
until_true "reply call hangs up by itself" reply_gone
x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "action ran: ${x#*|}" || no "action ran" "$x"
[ "$(D "run-as com.littyl.digix ls cache/speech" 2>/dev/null | grep -c wav)" -ge 1 ] && ok "speech was rendered" || no "speech was rendered" "no audio file"
[ "$(D dumpsys telecom | grep -E 'REDIRECTION_COMPLETED' | tail -1 | grep -c 'CRA.rC')" = "1" ] && ok "the dial itself became the reply call" || no "the dial itself became the reply call" "not redirected"
[ "$(log_rows)" = "$rows_before" ] && ok "reply stays out of call history" || no "reply stays out of call history" "row added"

print -- "--- 10. hanging up from the watch during the reply"
stop_app; t=$(now_ms)
dial 0103
caught=0
for i in {1..60}; do reply_active && { caught=1; break; }; done
[ "$caught" = "1" ] && ok "reply call connects" || no "reply call connects" "never seen"
D "cmd telecom stop-block-suppression" >/dev/null 2>&1
[ "$caught" = "1" ] && D input keyevent KEYCODE_ENDCALL
until_true "call ends when hung up" reply_gone
x=$(entry_since 0103 $t); [[ -n "$x" ]] && ok "action still finished: ${x#*|}" || no "action still finished" "none"
awake

print -- "--- 10b. a real call dialed during a reply always wins"
stop_app
dial 0103
until_true "reply call connects" reply_active
dial 5551234
expect_call "real call goes through"
until_true "reply call made way" reply_gone
end_calls
awake

print -- "--- 11. dialing a code during a real call never touches it"
stop_app
dial 5551234
expect_call "real call is up"
sleep 3
real=$(D dumpsys telecom | grep -E '^\s+\[?Call id=TC' | grep -oE 'id=TC@[0-9]+' | sort -u | head -1)
t=$(now_ms)
dial 0103
x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "action ran silently: ${x#*|}" || no "action ran silently" "$x"
reply_active && no "no reply call during a real call" "one appeared" || ok "no reply call during a real call"
sleep 5
[ "$(callstate)" = "2" ] && ok "real call stayed up" || no "real call stayed up" "state=$(callstate)"
# Older Android logs calls in another format, where the id can't be read.
if [ -n "$real" ]; then
  D dumpsys telecom | grep -E '^\s+\[?Call id=TC' | grep -q "$real, state=ACTIVE" && ok "same real call is still active" || no "same real call still active" "$real gone"
fi
end_calls

print -- "--- 12. spoken replies switched off"
stop_app
D "run-as com.littyl.digix sed -i 's#</map>#<boolean name=\"spoken_replies\" value=\"false\" /></map>#' shared_prefs/digix.xml"
c=$(newest_handled); t=$(now_ms)
dial 0103
expect_stopped "code cancelled, no call" "$c"
x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "action ran" || no "action ran" "$x"
reply_active && no "no reply call" "one appeared" || ok "no reply call"

if [ "$SDK" -ge 31 ]; then
  print -- "--- 12b. without phone access, a reply still works"
  stop_app
  D "run-as com.littyl.digix sed -i 's#<boolean name=\"spoken_replies\" value=\"false\" />##' shared_prefs/digix.xml"
  D pm revoke com.littyl.digix android.permission.READ_PHONE_STATE
  t=$(now_ms)
  dial 0103
  until_true "reply call connects" reply_active
  until_true "reply call hangs up by itself" reply_gone
  x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "action ran: ${x#*|}" || no "action ran" "$x"
  print -- "--- 12c. without phone access, a real call is still safe"
  dial 5551234
  expect_call "real call is up"
  sleep 3
  t=$(now_ms)
  dial 0103
  x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "action ran silently" || no "action ran silently" "$x"
  sleep 4
  [ "$(callstate)" = "2" ] && ok "real call stayed up" || no "real call stayed up" "state=$(callstate)"
  end_calls
  D pm grant com.littyl.digix android.permission.READ_PHONE_STATE
fi

print -- "--- 14. media keys go out only after the call ends"
stop_app
D "run-as com.littyl.digix sed -i 's#<boolean name=\"spoken_replies\" value=\"false\" />##' shared_prefs/digix.xml"
set_pref '0199' '0104'
stop_app
dial 0104
until_true "reply call connects" reply_active
until_true "reply call hangs up" reply_gone
gone=$(now_ms)
x=$(entry_since 0104 $((gone-1000))); [[ "$x" == DONE* ]] && ok "media key sent after the call: ${x#*|}" || no "media key sent after the call" "$x"

print -- "--- 15. Wi-Fi with no permission at all"
stop_app
set_pref 'FLASHLIGHT' 'WIFI'
wifi_is() { D cmd wifi status | head -1 | grep -q "$1"; }
wifi_is "enabled" || { D svc wifi enable; sleep 3; }
t=$(now_ms)
dial 0101
until_true "Wi-Fi turned off" "wifi_is disabled"
x=$(entry_since 0101 $t); [[ "$x" == DONE* ]] && ok "reported: ${x#*|}" || no "reported" "$x"
until_true "reply call hangs up" reply_gone
D locksettings set-pin 1379 >/dev/null 2>&1
D input keyevent KEYCODE_SLEEP; sleep 2
LOCKED=1
t=$(now_ms)
dial 0101
until_true "Wi-Fi back on from the lock screen" "wifi_is enabled"
x=$(entry_since 0101 $t); [[ "$x" == DONE* ]] && ok "reported: ${x#*|}" || no "reported" "$x"
until_true "reply call hangs up" reply_gone
D locksettings clear --old 1379 >/dev/null 2>&1
LOCKED=0
awake
stop_app
set_pref 'WIFI' 'FLASHLIGHT'

print -- "--- 16. the hotspot"
stop_app
set_pref 'FLASHLIGHT' 'HOTSPOT'
D appops set com.littyl.digix WRITE_SETTINGS allow
tethered() { D dumpsys tethering | grep -qE '^ *(wlan|swlan|softap|ap)[a-z_0-9]* - TetheredState'; }
untethered() { ! tethered; }
t=$(now_ms)
dial 0101
x=$(entry_since 0101 $t)
if [ "$SDK" -lt 36 ]; then
  [[ "$x" == DONE* ]] && ok "hotspot with Modify system settings: ${x#*|}" || no "hotspot with Modify system settings" "$x"
  until_true "hotspot is really up" tethered
  until_true "reply call hangs up" reply_gone
  t=$(now_ms)
  dial 0101
  x=$(entry_since 0101 $t); [[ "$x" == DONE* ]] && ok "hotspot off: ${x#*|}" || no "hotspot off" "$x"
  until_true "hotspot is really down" untethered
else
  [[ "$x" == NEEDS_SETUP* ]] && ok "says it needs setup on this version: ${x#*|}" || no "says it needs setup" "$x"
fi
until_true "reply call hangs up" reply_gone
D svc wifi enable
stop_app
set_pref 'HOTSPOT' 'FLASHLIGHT'

if [ -n "$MOCK_LOG" ]; then
  print -- "--- 17. voice assistant"
  stop_app
  set_pref 'PLAY_PAUSE' 'ASSISTANT'
  # Replies when nothing is set up.
  dial 0104
  until_true "call connects with no setup" reply_active
  until_true "explains and hangs up" reply_gone
  [ "$(wc -l < "$MOCK_LOG" | tr -d ' ')" = "0" ] && ok "nothing sent without setup" || no "nothing sent without setup" "requests"
  stop_app
  D pm grant com.littyl.digix android.permission.RECORD_AUDIO
  D "run-as com.littyl.digix sh -c 'cat > shared_prefs/assistant.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="provider">CUSTOM</string>
<string name="base_url">http://10.0.2.2:$MOCK_PORT/v1</string>
<string name="chat_model">test-model</string>
<string name="speech_model">test-speech</string>
<string name="test_key">test-key</string>
<boolean name="test_audio" value="true" />
</map>
XML
  t=$(now_ms)
  dial 0104
  until_true "conversation connects" reply_active
  until_true "foreground service while listening" service_foreground
  until_true "conversation ends itself on goodbye" reply_gone
  x=$(entry_since 0103 $t); [[ "$x" == DONE* ]] && ok "the assistant ran an action: ${x#*|}" || no "the assistant ran an action" "$x"
  x=$(entry_since 0104 $t); [[ "$x" == DONE* ]] && ok "conversation recorded: ${x#*|}" || no "conversation recorded" "$x"
  [ "$(grep -c 'audio/transcriptions' "$MOCK_LOG")" = "2" ] && ok "two spoken turns were sent" || no "two spoken turns were sent" "$(grep -c 'audio/transcriptions' "$MOCK_LOG")"
  grep -q 'audio/transcriptions.*riff=True' "$MOCK_LOG" && ok "audio was a real WAV file" || no "audio was a real WAV file" "no RIFF header"
  grep -q 'messages=4' "$MOCK_LOG" && ok "second turn carried the conversation history" || no "second turn carried history" "$(grep messages= "$MOCK_LOG" | tail -1)"
  [ "$(callstate)" = "0" ] && ok "phone network stayed idle" || no "phone network stayed idle" "state=$(callstate)"
  stop_app
  print -- "--- 18. voice assistant with a bad key"
  set_pref 'test-key' 'bad'
  D "run-as com.littyl.digix sed -i 's/test-key/bad/' shared_prefs/assistant.xml"
  dial 0104
  until_true "call connects" reply_active
  until_true "explains the problem and hangs up" reply_gone
fi

# Adds a code directly to the app's stored settings. Only for tests, with the app stopped.
add_code() { # code action phone name message
  local xml new
  xml=$(prefs)
  new=$(print -r -- "$xml" | python3 -c "
import sys,re,json,html
s=sys.stdin.read()
m=re.search(r'name=\"codes\">(.*?)</string>',s,re.S)
codes=[c for c in json.loads(html.unescape(m.group(1))) if c['c']!='$1']
codes.append({'c':'$1','a':'$2','p':'$3','n':'$4','m':'$5'})
print(s[:m.start(1)]+html.escape(json.dumps(codes,separators=(',',':')))+s[m.end(1):])")
  print -r -- "$new" | adb -s "$E" shell "run-as com.littyl.digix sh -c 'cat > shared_prefs/digix.xml'"
}
sent_messages() { D "content query --uri content://sms/sent --projection address:body" 2>/dev/null; }
sent_count() { sent_messages | grep -c 'Row:'; }

print -- "--- 19. speed dial becomes a real call"
stop_app
add_code 0201 CALL_CONTACT 5559876 Mom ""
dial 0201
expect_call "dialing the code really calls the person"
# The phone's history keeps the dialed code, so check Android's own record of the redirection.
adb -s "$E" logcat -d | grep -q 'outgoing call with gateway handle' && ok "Android redirected the dial to the chosen number" || no "Android redirected the dial to the chosen number" "no record"
end_calls

print -- "--- 20. a text message"
stop_app
D pm grant com.littyl.digix android.permission.SEND_SMS
D pm grant com.littyl.digix android.permission.ACCESS_FINE_LOCATION
D pm grant com.littyl.digix android.permission.ACCESS_COARSE_LOCATION
D pm grant com.littyl.digix android.permission.CALL_PHONE
add_code 0202 SEND_SMS 5551111 Mom "On my way home"
before=$(sent_count)
t=$(now_ms)
dial 0202
x=$(entry_since 0202 $t); [[ "$x" == DONE* ]] && ok "reported: ${x#*|}" || no "text reported" "$x"
until_true "reply call hangs up" reply_gone
[ "$(sent_count)" -gt "$before" ] && ok "text is in the sent box" || no "text is in the sent box" "$(sent_count) vs $before"
sent_messages | grep -q 'On my way home' && ok "message text is correct" || no "message text is correct" "$(sent_messages | tail -1 | cut -c1-80)"

print -- "--- 21. sharing my location"
stop_app
add_code 0203 SHARE_LOCATION 5552222 Mom ""
t=$(now_ms)
dial 0203
# A real GPS keeps reporting, but this simulated one only reports when told to.
for i in 1 2 3 4 5 6; do adb -s "$E" emu geo fix 77.594$i 12.971$i >/dev/null; sleep 1; done
x=$(entry_since 0203 $t); [[ "$x" == DONE* ]] && ok "reported: ${x#*|}" || no "location reported" "$x"
until_true "reply call hangs up" reply_gone
sent_messages | grep -qE 'maps.google.com/\?q=12\.97' && ok "text contains the real map position" || no "text contains the real map position" "$(sent_messages | grep -c maps.google)"

print -- "--- 22. a text with no signal reports failure honestly"
stop_app
if [ "$SDK" -ge 30 ]; then
  D cmd connectivity airplane-mode enable 2>/dev/null
  sleep 4
  t=$(now_ms)
  dial 0202
  x=$(entry_since 0202 $t)
  [[ "$x" == FAILED* ]] && ok "says it failed: ${x#*|}" || no "says it failed" "$x"
  D cmd connectivity airplane-mode disable 2>/dev/null
  sleep 6
  until_true "reply call hangs up" reply_gone
fi

print -- "--- 23. SOS"
stop_app
add_code 0911 SOS "" "" ""
D "run-as com.littyl.digix sh -c 'cat > shared_prefs/sos.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="contacts">[{"n":"Mom","p":"5553333"},{"n":"Sis","p":"5554444"}]</string>
<string name="message">I feel unsafe, please help</string>
</map>
XML
# Master switch off, phone locked: the hardest case on purpose.
set_pref 'name=\"enabled\" value=\"true\"' 'name=\"enabled\" value=\"false\"'
adb -s "$E" emu geo fix 77.5946 12.9716 >/dev/null
D locksettings set-pin 1379 >/dev/null 2>&1
D input keyevent KEYCODE_SLEEP; sleep 8
LOCKED=1
before=$(sent_count)
t=$(now_ms)
dial 0911
sos_running() { D dumpsys activity services com.littyl.digix | grep -q 'SosService'; }
until_true "SOS service runs while off and locked" sos_running
sos_first() { sent_messages | grep -q 'I feel unsafe, please help'; }
until_true "first alert goes out" sos_first
sos_both() { [ "$(sent_messages | grep -c 'I feel unsafe, please help')" -ge 2 ]; }
until_true "alert reached both contacts" sos_both
# A position first seen after the alert started, so finding it in a text proves it was tracked live.
fresh_fix() { adb -s "$E" emu geo fix 77.6123 12.9345 >/dev/null; sent_messages | grep -qE '12\.934[0-9]'; }
until_true "a live position reaches the contacts" fresh_fix
sos_located() { sent_messages | grep -q 'maps.google.com'; }
until_true "alert includes a map link" sos_located
x=$(entry_since 0911 $t); [[ "$x" == DONE* ]] && ok "recorded: ${x#*|}" || no "SOS recorded" "$x"
until_true "the silent call is over" reply_gone
[ "$(callstate)" = "0" ] && ok "no network call" || no "no network call" "state=$(callstate)"
print -- "      kill the app mid-alert"
pid=$(D pidof com.littyl.digix | tr -d '\r')
D run-as com.littyl.digix kill -9 "$pid"
sleep 2
sos_back() { [ -n "$(D pidof com.littyl.digix | tr -d '\r')" ] && D dumpsys activity services com.littyl.digix | grep -q 'SosService'; }
until_true "alert restarts after being killed" sos_back
before_stop=$(sent_count)
dial 0911
sos_stopped() { ! sos_running; }
until_true "dialing again stops it" sos_stopped
sos_told() { sent_messages | grep -q 'no longer sharing'; }
until_true "contacts are told it stopped" sos_told
LOCKED=0
D locksettings clear --old 1379 >/dev/null 2>&1
awake
until_true "reply call hangs up" reply_gone
stop_app
set_pref 'name=\"enabled\" value=\"false\"' 'name=\"enabled\" value=\"true\"'

if [ -n "$HOME_FAKE" ]; then
  print -- "--- 24. Google Home, against the stand-in"
  add_home_code() { # code device name command
    local xml new
    xml=$(prefs)
    new=$(print -r -- "$xml" | python3 -c "
import sys,re,json,html
s=sys.stdin.read()
m=re.search(r'name=\"codes\">(.*?)</string>',s,re.S)
codes=[c for c in json.loads(html.unescape(m.group(1))) if c['c']!='$1']
codes.append({'c':'$1','a':'GOOGLE_HOME','hd':'$2','hn':'$3','hc':'$4'})
print(s[:m.start(1)]+html.escape(json.dumps(codes,separators=(',',':')))+s[m.end(1):])")
    print -r -- "$new" | adb -s "$E" shell "run-as com.littyl.digix sh -c 'cat > shared_prefs/digix.xml'"
  }
  home_prefs() { # file, boolean name, value
    D "run-as com.littyl.digix sh -c \"echo '<map><boolean name=\\\"$2\\\" value=\\\"$3\\\" /></map>' > shared_prefs/$1.xml\""
  }
  home_dial() { # code, label, expected status, expected message
    local t x
    t=$(now_ms)
    dial "$1"
    x=$(entry_since "$1" $t)
    [[ "$x" == "$3|$4" ]] && ok "$2: ${x#*|}" || no "$2" "$x"
    until_true "$2: reply call hangs up" reply_gone
    [ "$(callstate)" = "0" ] && ok "$2: no network call" || no "$2: no network call" "state=$(callstate)"
  }
  stop_app
  home_prefs digix_fake_home granted false
  home_prefs digix_home connected false
  add_home_code 0301 light "Bedroom light" ON
  add_home_code 0302 light "Bedroom light" OFF
  add_home_code 0303 ac "Air conditioner" TOGGLE
  add_home_code 0304 offline "Porch light" ON
  add_home_code 0305 hang "Old plug" ON
  add_home_code 0306 removed "Lost lamp" OFF
  home_dial 0301 "not connected" NEEDS_SETUP "Google Home isn't connected. Open Digix to connect it."
  stop_app
  home_prefs digix_fake_home granted true
  home_prefs digix_home connected true
  home_dial 0301 "turn on from a dead app" DONE "Bedroom light on"
  home_dial 0302 "turn off" DONE "Bedroom light off"
  home_dial 0303 "switch" DONE "Air conditioner on"
  home_dial 0303 "switch again" DONE "Air conditioner off"
  home_dial 0304 "device offline" FAILED "Couldn't reach Porch light. Check it in Google Home."
  home_dial 0306 "device removed" FAILED "Lost lamp isn't in your Google Home anymore."
  home_dial 0305 "no answer" FAILED "Couldn't reach Old plug. Check it in Google Home."
  print -- "      with the phone locked"
  D locksettings set-pin 1379 >/dev/null 2>&1
  D input keyevent KEYCODE_SLEEP
  LOCKED=1
  sleep 2
  home_dial 0301 "turn on while locked" DONE "Bedroom light on"
  LOCKED=0
  D locksettings clear --old 1379 >/dev/null 2>&1
  awake
  stop_app
fi

print -- "--- 13. crashes"
n=$(adb -s "$E" logcat -d -b crash | grep -c 'Process: com.littyl.digix')
[ "$n" = "0" ] && ok "no crashes during the run" || no "no crashes" "$n crash(es)"

print "\nRESULT: $PASS passed, $FAIL failed"
[ "$FAIL" = "0" ]
