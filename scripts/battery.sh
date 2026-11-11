#!/usr/bin/env bash
# Measures the News app's battery use with Android's own accounting (dumpsys batterystats), on the connected phone
# (else the emulator). See docs/battery.md.
#   ./scripts/battery.sh start     reset the statistics; then use the phone normally, unplugged, for some hours
#   ./scripts/battery.sh report    News's share since the reset: estimated mAh, CPU, network, background jobs
#   PACKAGE=news.client.uilab ./scripts/battery.sh report   another app id; DEVICE=emulator|phone|<serial> to choose
set -euo pipefail

cmd="${1:-report}"
package="${PACKAGE:-news.client}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$sdk/platform-tools/adb"

devices=$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
case "${DEVICE:-}" in
    "") serial=$(grep -v '^emulator-' <<<"$devices" | head -1 || true)
        [[ -n "$serial" ]] || serial=$(grep '^emulator-' <<<"$devices" | head -1 || true) ;;
    phone) serial=$(grep -v '^emulator-' <<<"$devices" | head -1 || true) ;;
    emulator) serial=$(grep '^emulator-' <<<"$devices" | head -1 || true) ;;
    *) serial=$(grep -Fx "$DEVICE" <<<"$devices" || true) ;;
esac
[[ -n "$serial" ]] || { echo "No phone or emulator found." >&2; "$adb" devices -l >&2; exit 1; }
sh() { "$adb" -s "$serial" shell "$@" | tr -d '\r'; }

case "$cmd" in
start)
    sh dumpsys batterystats --reset >/dev/null
    echo "Battery statistics reset on $serial ($(sh getprop ro.product.model))."
    echo "Android only counts time on battery: keep the phone unplugged (wireless debugging is fine)."
    echo "Then run: ./scripts/battery.sh report"
    ;;
report)
    uid=$(sh cmd package list packages -U "$package" | grep "package:$package " | grep -o 'uid:[0-9]*' | cut -d: -f2)
    [[ -n "$uid" ]] || { echo "$package is not installed on $serial." >&2; exit 1; }
    app="u0a$((uid - 10000))"
    stats=$(sh dumpsys batterystats --charged)
    echo "$package ($app) on $serial ($(sh getprop ro.product.model)), since the last reset:"
    grep -m1 -E '^ *Time on battery:' <<<"$stats" | sed 's/^ */  /' || true
    grep -m1 -E 'Capacity: .*drain' <<<"$stats" | sed 's/^ */  Phone: /' || true
    line=$(grep -m1 -E "^ *UID $app: " <<<"$stats" || true)
    if [[ -z "$line" ]]; then
        echo "  Estimated use: none recorded (the app has not run on battery since the reset)."
    else
        total=$(grep -m1 -E 'Computed drain: ' <<<"$stats" | sed -E 's/.*Computed drain: ([0-9.]+).*/\1/')
        mah=$(sed -E "s/^ *UID $app: ([0-9.]+).*/\1/" <<<"$line")
        echo "  Estimated use: $mah mAh$(awk -v a="$mah" -v t="$total" 'BEGIN { if (t > 0) printf " (%.1f%% of the phone'"'"'s computed drain)", 100 * a / t }')"
        echo "  $(sed -E "s/^ *UID $app: [0-9.]+ //" <<<"$line")"
    fi
    # The app's own block: CPU, network, wakelocks, background jobs.
    awk -v app="  $app:" '$0 == app { on = 1; next } on && /^  [^ ]/ { exit } on' <<<"$stats" \
        | grep -E 'Wi-Fi network|Mobile network|Wifi Running|Total cpu|Wake lock|Job |Jobs|Foreground|Background|Cached for' \
        | sed 's/^ */  /' | head -25 || true
    ;;
*)
    echo "Usage: $0 start|report" >&2
    exit 2
    ;;
esac
