#!/usr/bin/env bash
# Build the UI Lab app (the debug build under its own id, news.client.uilab, so it sits beside the app), install it on the
# connected phone (else the first running emulator) and open it: the app's screens and suggested designs for them,
# with made-up data. See docs/ui-lab.md.
#   ./scripts/ui-lab.sh                    the list of screens
#   ./scripts/ui-lab.sh stories            a screen's options: the current one first, then the others
#   ./scripts/ui-lab.sh stories current    one option (ids in LabScreens.kt)
#   DEVICE=emulator ./scripts/ui-lab.sh    the emulator even when a phone is connected (or DEVICE=phone / <serial>)
set -euo pipefail

screen="${1:-}"
option="${2:-}"
cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"
# Gradle needs a JDK: JAVA_HOME, else Homebrew's openjdk@21, else the one bundled with Android Studio.
if [[ -z "${JAVA_HOME:-}" ]]; then
    for jdk in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
               "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        if [[ -x "$jdk/bin/java" ]]; then export JAVA_HOME="$jdk"; break; fi
    done
fi

devices=$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
case "${DEVICE:-}" in
    "") serial=$(grep -v '^emulator-' <<<"$devices" | head -1 || true)
        [[ -n "$serial" ]] || serial=$(grep '^emulator-' <<<"$devices" | head -1 || true) ;;
    phone) serial=$(grep -v '^emulator-' <<<"$devices" | head -1 || true) ;;
    emulator) serial=$(grep '^emulator-' <<<"$devices" | head -1 || true) ;;
    *) serial=$(grep -Fx "$DEVICE" <<<"$devices" || true) ;;
esac
if [[ -z "$serial" ]]; then
    echo "No phone or emulator found. Connected devices:" >&2
    "$adb" devices -l >&2
    exit 1
fi

echo "Device: $serial ($("$adb" -s "$serial" shell getprop ro.product.model | tr -d '\r'))"
./gradlew --console=plain -q :app:android:assembleUiLab
"$adb" -s "$serial" install -r app/android/build/outputs/apk/uiLab/android-uiLab.apk
# -S stops the old process so the new build runs; --user current: a phone with a Secure Folder refuses -S without it.
# -f 0x10008000 (new task, clearing the old one): otherwise Android resumes the lab's earlier task, ignoring the screen asked for.
"$adb" -s "$serial" shell am start --user current -S -f 0x10008000 -n news.client.uilab/news.app.lab.UiLabActivity \
    ${screen:+--es screen "$screen"} ${option:+--es option "$option"}
