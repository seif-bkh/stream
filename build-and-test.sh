#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly APP_ID="com.seif.stream"
readonly MIN_DEVICE_API=26
readonly COMPILE_SDK=35
readonly BUILD_TOOLS_VERSION="35.0.0"
readonly APP_APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
readonly TEST_APK="$SCRIPT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

MODE="auto"
CLEAN=false
DEVICE_SERIAL=""
JAVA_BIN=""
ADB=""
ANDROID_SDK=""

if [[ -t 1 ]]; then
    readonly BOLD=$'\033[1m'
    readonly GREEN=$'\033[32m'
    readonly YELLOW=$'\033[33m'
    readonly RED=$'\033[31m'
    readonly RESET=$'\033[0m'
else
    readonly BOLD=""
    readonly GREEN=""
    readonly YELLOW=""
    readonly RED=""
    readonly RESET=""
fi

usage() {
    cat <<'EOF'
Build and test Stream on Linux, optionally using a connected Android phone.

Usage:
  ./build-and-test.sh [options]

Options:
  --linux-only       Run unit tests, lint, and APK builds without using adb.
  --device           Require a device, install the APK, and launch Stream.
  --serial SERIAL    Use a specific adb device; implies --device.
  --clean            Run Gradle's clean task before all checks.
  -h, --help         Show this help.

Default behavior is automatic: all Linux checks run first. If exactly one
Android device is connected and authorized, the script also installs the debug
APK and launches Stream for a safe smoke/manual UI test. If no phone is ready,
the Linux checks still succeed and the APK path is printed.

Phone requirements:
  - Android 8.0 / API 26 or newer
  - Developer options and USB debugging enabled
  - The computer authorized when the phone shows the RSA prompt

The script never uninstalls or clears the Stream app and does not run the
storage-mutating instrumentation suite on your phone. Existing data is retained
by adb's replace-install mode. Before deploying, let any open capture reach the
"saved" state and leave Stream. A build signed with a different key may require
manual uninstalling; export important data before doing that.
EOF
}

section() {
    printf '\n%s%s==> %s%s\n' "$BOLD" "$GREEN" "$1" "$RESET"
}

info() {
    printf '%s[info]%s %s\n' "$GREEN" "$RESET" "$1"
}

warn() {
    printf '%s[warn]%s %s\n' "$YELLOW" "$RESET" "$1" >&2
}

fail() {
    printf '%s[error]%s %s\n' "$RED" "$RESET" "$1" >&2
    exit 1
}

quote_command() {
    printf '  '
    printf '%q ' "$@"
    printf '\n'
}

parse_args() {
    while (($# > 0)); do
        case "$1" in
            --linux-only)
                [[ "$MODE" != "required" ]] || fail "--linux-only cannot be combined with --device or --serial."
                MODE="off"
                ;;
            --device)
                [[ "$MODE" != "off" ]] || fail "--device cannot be combined with --linux-only."
                MODE="required"
                ;;
            --serial)
                [[ $# -ge 2 ]] || fail "--serial requires a device serial value."
                [[ -n "$2" ]] || fail "--serial cannot be empty."
                [[ "$MODE" != "off" ]] || fail "--serial cannot be combined with --linux-only."
                DEVICE_SERIAL="$2"
                MODE="required"
                shift
                ;;
            --clean)
                CLEAN=true
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                fail "Unknown option: $1. Run ./build-and-test.sh --help for usage."
                ;;
        esac
        shift
    done
}

check_linux() {
    [[ "$(uname -s)" == "Linux" ]] || fail "This helper targets Linux hosts."
    [[ -f "$SCRIPT_DIR/gradlew" ]] || fail "Gradle wrapper not found at $SCRIPT_DIR/gradlew."
    [[ -x "$SCRIPT_DIR/gradlew" ]] || fail "gradlew is not executable. Run: chmod +x gradlew"
}

check_java() {
    if [[ -n "${JAVA_HOME:-}" ]]; then
        [[ -x "$JAVA_HOME/bin/java" ]] || fail "JAVA_HOME does not contain an executable bin/java: $JAVA_HOME"
        JAVA_BIN="$JAVA_HOME/bin/java"
    elif command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
    else
        fail "Java was not found. Install JDK 17 (for example Temurin 17) and set JAVA_HOME."
    fi

    local java_spec
    local java_major
    java_spec="$(
        "$JAVA_BIN" -XshowSettings:properties -version 2>&1 |
            awk -F= '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{gsub(/[[:space:]]/, "", $2); print $2; exit}'
    )"
    [[ -n "$java_spec" ]] || fail "Could not determine the installed Java version."

    java_major="${java_spec#1.}"
    java_major="${java_major%%.*}"
    [[ "$java_major" =~ ^[0-9]+$ ]] || fail "Unrecognized Java version: $java_spec"
    ((java_major >= 17)) || fail "JDK 17 or newer is required; found Java $java_spec."

    info "Java $java_spec: $JAVA_BIN"
    if [[ -n "${JAVA_HOME:-}" ]]; then
        info "JAVA_HOME=$JAVA_HOME"
    else
        warn "JAVA_HOME is not set; Gradle will use the java executable from PATH."
    fi
}

read_local_sdk() {
    local properties_file="$SCRIPT_DIR/local.properties"
    local sdk_value=""
    [[ -f "$properties_file" ]] || return 0

    sdk_value="$(awk -F= '$1 == "sdk.dir" {sub(/^[^=]*=/, ""); print; exit}' "$properties_file")"
    sdk_value="${sdk_value//\\:/:}"
    sdk_value="${sdk_value//\\ / }"
    printf '%s' "$sdk_value"
}

find_android_sdk() {
    local local_sdk
    local candidate
    local -a candidates=()

    local_sdk="$(read_local_sdk)"
    candidates+=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "$local_sdk"
        "$HOME/Android/Sdk"
        "$HOME/Android/sdk"
        "/opt/android-sdk"
        "/usr/local/android-sdk"
    )

    for candidate in "${candidates[@]}"; do
        if [[ -n "$candidate" && -d "$candidate" ]]; then
            ANDROID_SDK="$(cd -- "$candidate" && pwd)"
            break
        fi
    done

    [[ -n "$ANDROID_SDK" ]] || fail \
        "Android SDK not found. Install Android Studio or Android command-line tools, then set ANDROID_HOME (usually \$HOME/Android/Sdk)."

    export ANDROID_HOME="$ANDROID_SDK"
    export ANDROID_SDK_ROOT="$ANDROID_SDK"
    info "Android SDK: $ANDROID_SDK"

    local -a missing=()
    [[ -f "$ANDROID_SDK/platforms/android-$COMPILE_SDK/android.jar" ]] ||
        missing+=("platforms;android-$COMPILE_SDK")
    [[ -d "$ANDROID_SDK/build-tools/$BUILD_TOOLS_VERSION" ]] ||
        missing+=("build-tools;$BUILD_TOOLS_VERSION")

    if ((${#missing[@]} > 0)); then
        local sdkmanager=""
        sdkmanager="$(find "$ANDROID_SDK/cmdline-tools" -path '*/bin/sdkmanager' -type f 2>/dev/null | sort -V | tail -n 1 || true)"
        if [[ -x "$sdkmanager" ]]; then
            printf '%s[error]%s Required Android SDK components are missing. Install them with:\n' "$RED" "$RESET" >&2
            quote_command "$sdkmanager" "platform-tools" "${missing[@]}" >&2
            printf 'Accept licenses first if needed with: %q --licenses\n' "$sdkmanager" >&2
        else
            printf '%s[error]%s Required Android SDK components are missing: %s\n' \
                "$RED" "$RESET" "${missing[*]}" >&2
            printf 'Install Android SDK Command-line Tools and then use sdkmanager.\n' >&2
        fi
        exit 1
    fi
}

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        ADB="$(command -v adb)"
    elif [[ -x "$ANDROID_SDK/platform-tools/adb" ]]; then
        ADB="$ANDROID_SDK/platform-tools/adb"
    else
        ADB=""
    fi
}

run_host_checks() {
    section "Running Linux unit tests, lint, and APK builds"

    local -a gradle_command=("$SCRIPT_DIR/gradlew")
    if [[ "$CLEAN" == true ]]; then
        gradle_command+=(clean)
    fi
    gradle_command+=(
        testDebugUnitTest
        lintDebug
        assembleDebug
        assembleDebugAndroidTest
        --stacktrace
    )

    quote_command "${gradle_command[@]}"
    (
        cd "$SCRIPT_DIR"
        "${gradle_command[@]}"
    )

    [[ -f "$APP_APK" ]] || fail "Gradle succeeded but the app APK was not found at $APP_APK."
    [[ -f "$TEST_APK" ]] || fail "Gradle succeeded but the test APK was not found at $TEST_APK."

    info "Linux checks passed."
    info "App APK: $APP_APK ($(du -h "$APP_APK" | awk '{print $1}'))"
    info "Unit-test report: $SCRIPT_DIR/app/build/reports/tests/testDebugUnitTest/index.html"
    info "Lint report: $SCRIPT_DIR/app/build/reports/lint-results-debug.html"
}

discover_device() {
    find_adb
    if [[ -z "$ADB" ]]; then
        if [[ "$MODE" == "required" ]]; then
            fail "adb was not found. Install Android SDK Platform-Tools or add it to PATH."
        fi
        warn "adb is unavailable; skipping connected-phone tests."
        return 1
    fi

    "$ADB" start-server >/dev/null

    local -a ready_devices=()
    local serial
    local state
    while read -r serial state _; do
        [[ -n "$serial" && "$serial" != "List" ]] || continue
        if [[ "$state" == "device" ]]; then
            ready_devices+=("$serial")
        fi
    done < <("$ADB" devices -l)

    if [[ -n "$DEVICE_SERIAL" ]]; then
        state="$("$ADB" devices | awk -v wanted="$DEVICE_SERIAL" '$1 == wanted {print $2; exit}')"
        case "$state" in
            device)
                return 0
                ;;
            unauthorized)
                fail "Device $DEVICE_SERIAL is unauthorized. Unlock it and accept the USB-debugging RSA prompt."
                ;;
            offline)
                fail "Device $DEVICE_SERIAL is offline. Reconnect it and retry."
                ;;
            *)
                fail "Device $DEVICE_SERIAL is not available. Check: $ADB devices -l"
                ;;
        esac
    fi

    if ((${#ready_devices[@]} == 0)); then
        if "$ADB" devices | grep -q 'unauthorized'; then
            warn "A phone is connected but unauthorized. Unlock it and accept the USB-debugging RSA prompt."
        elif "$ADB" devices | grep -q 'offline'; then
            warn "A phone is visible to adb but offline. Reconnect it before device testing."
        fi

        if [[ "$MODE" == "required" ]]; then
            "$ADB" devices -l >&2
            fail "No authorized Android device is connected."
        fi
        info "No authorized phone detected; Linux-only testing is complete."
        return 1
    fi

    if ((${#ready_devices[@]} > 1)); then
        "$ADB" devices -l >&2
        if [[ "$MODE" == "required" ]]; then
            fail "Multiple devices are connected. Select one with --serial SERIAL."
        fi
        warn "Multiple authorized devices detected; skipping phone tests. Re-run with --serial SERIAL."
        return 1
    fi

    DEVICE_SERIAL="${ready_devices[0]}"
    return 0
}

install_apk() {
    local label="$1"
    local apk="$2"
    local output

    info "Installing $label without clearing existing app data..."
    if output="$("$ADB" -s "$DEVICE_SERIAL" install -r -t -d "$apk" 2>&1)"; then
        printf '%s\n' "$output"
    else
        printf '%s\n' "$output" >&2
        if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$output"; then
            warn "The installed Stream app uses a different signing key."
            warn "This script will not uninstall it because uninstalling would erase its local notes."
            warn "Export/back up important data, then uninstall it manually if you want this debug build."
        fi
        fail "Failed to install $label on $DEVICE_SERIAL."
    fi
}

run_device_checks() {
    section "Deploying to Android device $DEVICE_SERIAL"

    local api_level
    local manufacturer
    local model
    local android_version
    local resumed_activity
    api_level="$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
    manufacturer="$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
    model="$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.product.model | tr -d '\r')"
    android_version="$("$ADB" -s "$DEVICE_SERIAL" shell getprop ro.build.version.release | tr -d '\r')"

    [[ "$api_level" =~ ^[0-9]+$ ]] || fail "Could not read Android API level from $DEVICE_SERIAL."
    ((api_level >= MIN_DEVICE_API)) || fail \
        "Device API $api_level is unsupported; Stream requires API $MIN_DEVICE_API or newer."

    info "Device: ${manufacturer:-Android} ${model:-device}, Android ${android_version:-unknown}, API $api_level"

    resumed_activity="$(
        "$ADB" -s "$DEVICE_SERIAL" shell dumpsys activity activities 2>/dev/null |
            grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' |
            head -n 1 || true
    )"
    if [[ "$resumed_activity" == *"$APP_ID"* ]]; then
        fail "Stream is currently open on the phone. Wait for its saved indicator, leave the app, and run this script again."
    fi

    install_apk "Stream debug APK" "$APP_APK"

    section "Launching Stream"
    local launch_output
    if launch_output="$(
        "$ADB" -s "$DEVICE_SERIAL" shell am start -W -n "$APP_ID/.MainActivity" 2>&1
    )"; then
        printf '%s\n' "$launch_output"
    else
        printf '%s\n' "$launch_output" >&2
        fail "The APK installed, but Stream could not be launched."
    fi

    if grep -q '^Status:' <<<"$launch_output" &&
        ! grep -q '^Status: ok' <<<"$launch_output"; then
        fail "Android reported an unsuccessful launch status."
    fi

    local installed_path
    installed_path="$("$ADB" -s "$DEVICE_SERIAL" shell pm path "$APP_ID" | tr -d '\r')"
    [[ "$installed_path" == package:* ]] || fail "Android did not report Stream as installed after deployment."

    info "Install and launch smoke test passed."
    info "Unlock the phone if necessary; Capture should have focus and show the keyboard."
    cat <<'EOF'

Manual phone checks:
  1. Confirm Capture opens immediately with a cursor and keyboard.
  2. Type a thought; confirm the timestamp appears on the first keystroke.
  3. Stop typing for two seconds; confirm the status changes to "saved".
  4. Swipe right to Log, verify/search the entry, then swipe left to Capture.
  5. Tap the entry, edit it, and confirm its original timestamp stays fixed.
  6. Move entries to Trash; test restore, permanent delete, and confirmed Empty Trash.
  7. Long-press the launcher icon and try the Capture shortcut.
  8. Open Settings and test export/import with a disposable export file.

Connected instrumentation is intentionally not run on this phone because the
current persistence test uses target-app storage. CI still compiles that test
APK; use a disposable emulator/device if you later choose to execute it.
EOF
}

main() {
    parse_args "$@"
    check_linux

    section "Checking build prerequisites"
    check_java
    find_android_sdk

    run_host_checks

    if [[ "$MODE" == "off" ]]; then
        section "Done"
        info "Linux-only validation passed."
        find_adb
        if [[ -n "$ADB" ]]; then
            info "Optional manual phone install:"
            quote_command "$ADB" install -r -t -d "$APP_APK"
        else
            info "Install Android SDK Platform-Tools if you later want to deploy this APK with adb."
        fi
        return 0
    fi

    if discover_device; then
        run_device_checks
    fi

    section "Done"
    info "Build and requested tests completed successfully."
}

main "$@"
