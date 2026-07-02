#!/usr/bin/env bash
# Run the unit suite inside a linux/amd64 container = faithful CI-equivalent oracle
# (native x86_64 Linux userland, unlike macOS Rosetta which lacks AndroidKeyStore).
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
# Isolate the x86 container's Gradle home from the host arm64 ~/.gradle. Sharing it corrupts
# cross-arch artifacts (e.g. the jdkImage transform) and breaks the host build afterwards.
# Mount a named volume at this path to persist deps across runs without touching the host cache.
export GRADLE_USER_HOME=/sdk/gradle-home
apt-get update -qq && apt-get install -y -qq unzip curl >/dev/null

SDK=/sdk
mkdir -p "$SDK/cmdline-tools"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  echo "== downloading cmdline-tools =="
  curl -sSL -o /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q /tmp/clt.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export PATH="$SDK/cmdline-tools/latest/bin:$PATH"
yes 2>/dev/null | sdkmanager --sdk_root="$SDK" --licenses >/dev/null || true
echo "== installing platform/build-tools =="
sdkmanager --sdk_root="$SDK" "platform-tools" "platforms;android-34" "build-tools;35.0.0" >/dev/null

cd /work
# Back up and restore the host's local.properties so this container run does not clobber the
# host SDK path (the working dir is bind-mounted from the host).
[ -f local.properties ] && cp local.properties /tmp/local.properties.host
restore_lp() { if [ -f /tmp/local.properties.host ]; then cp /tmp/local.properties.host local.properties; else rm -f local.properties; fi; }
trap restore_lp EXIT
printf 'sdk.dir=%s\n' "$SDK" > local.properties
echo "== arch check ==" && uname -m
echo "== running testDebugUnitTest =="
./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks --console=plain
