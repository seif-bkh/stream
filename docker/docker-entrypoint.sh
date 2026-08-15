#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEV_USER="android"
readonly DEV_HOME="/home/android"
readonly WORKSPACE="${WORKSPACE_DIR:-/workspace}"

die() {
    printf '[container error] %s\n' "$1" >&2
    exit 1
}

[[ -d "$WORKSPACE" ]] || die "Workspace mount not found at $WORKSPACE"

TARGET_UID="${LOCAL_UID:-$(stat -c '%u' "$WORKSPACE")}"
TARGET_GID="${LOCAL_GID:-$(stat -c '%g' "$WORKSPACE")}"

[[ "$TARGET_UID" =~ ^[0-9]+$ ]] || die "LOCAL_UID must be numeric."
[[ "$TARGET_GID" =~ ^[0-9]+$ ]] || die "LOCAL_GID must be numeric."

if [[ "$TARGET_UID" == "0" ]]; then
    # This can occur with root-owned workspaces or some remote Docker mounts.
    # Running as root is preferable to making the bind mount unwritable.
    export HOME=/root
    export ANDROID_USER_HOME=/root/.android
    export GRADLE_USER_HOME=/root/.gradle
    mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
    exec "$@"
fi

uid_owner="$(getent passwd "$TARGET_UID" | cut -d: -f1 || true)"
if [[ -n "$uid_owner" && "$uid_owner" != "$DEV_USER" ]]; then
    die "Workspace UID $TARGET_UID is already owned by container user $uid_owner. Set LOCAL_UID explicitly."
fi

target_group="$(getent group "$TARGET_GID" | cut -d: -f1 || true)"
if [[ -z "$target_group" ]]; then
    current_group="$(id -gn "$DEV_USER")"
    groupmod --gid "$TARGET_GID" "$current_group"
    target_group="$current_group"
else
    usermod --gid "$target_group" "$DEV_USER"
fi

if [[ "$(id -u "$DEV_USER")" != "$TARGET_UID" ]]; then
    usermod --uid "$TARGET_UID" "$DEV_USER"
fi

mkdir -p "$DEV_HOME/.gradle" "$DEV_HOME/.android"
chown "$DEV_USER:$target_group" "$DEV_HOME"
for cache_dir in "$DEV_HOME/.gradle" "$DEV_HOME/.android"; do
    cache_uid="$(stat -c '%u' "$cache_dir")"
    cache_gid="$(stat -c '%g' "$cache_dir")"
    if [[ "$cache_uid" != "$TARGET_UID" || "$cache_gid" != "$TARGET_GID" ]]; then
        chown -R "$DEV_USER:$target_group" "$cache_dir"
    fi
done

export HOME="$DEV_HOME"
export ANDROID_USER_HOME="$DEV_HOME/.android"
export GRADLE_USER_HOME="$DEV_HOME/.gradle"

if (($# == 0)); then
    set -- bash
fi

exec setpriv --reuid="$DEV_USER" --regid="$target_group" --init-groups "$@"
