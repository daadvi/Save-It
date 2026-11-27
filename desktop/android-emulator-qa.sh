set -euo pipefail
adb install /tmp/save-it-old.apk
adb shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT https://example.org/migracao -n br.com.saveit/.MainActivity
for i in $(seq 1 10); do
  if adb shell run-as br.com.saveit cat shared_prefs/collection.xml | grep migra; then break; fi
  sleep 2
done
adb shell run-as br.com.saveit cat shared_prefs/collection.xml | grep migra
adb install -r /tmp/save-it.apk
adb shell am start -W -n br.com.saveit/.MainActivity
pid=$(adb shell pidof br.com.saveit | tr -d '\r')
adb forward tcp:9224 "localabstract:webview_devtools_remote_$pid"
python3 -m http.server 8765 --bind 127.0.0.1 --directory desktop >/tmp/html-server.log 2>&1 &
adb reverse tcp:8765 tcp:8765
adb shell 'echo "chrome --no-first-run --disable-fre --no-default-browser-check" > /data/local/tmp/chrome-command-line'
adb shell am set-debug-app com.android.chrome
adb shell am start -W -a android.intent.action.VIEW -d http://127.0.0.1:8765/Save-It.html -p com.android.chrome
adb forward tcp:9223 localabstract:chrome_devtools_remote
for i in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:9223/json/list | grep -q Save-It.html; then break; fi
  sleep 2
done
node desktop/android-emulator-qa.cjs
