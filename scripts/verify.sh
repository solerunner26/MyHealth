#!/usr/bin/env bash
# Definition-of-Done gate: build, all JVM tests, lint (errors fail the build), instrumented test APK compiles.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew assembleDebug testDebugUnitTest :domain:test lintDebug assembleDebugAndroidTest "$@"
echo "Verification passed."
