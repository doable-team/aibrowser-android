#!/usr/bin/env bash
# Use the workspace-local toolchain installed for this checkout.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$ROOT/.local/amazon-corretto-17.0.20.12.1-linux-x64"
export ANDROID_HOME="$ROOT/.local/android-sdk"
export ANDROID_USER_HOME="$ROOT/.local/android-user"
export GRADLE_USER_HOME="$ROOT/.local/gradle"
exec "$ROOT/gradlew" -p "$ROOT" -Pkotlin.compiler.execution.strategy=in-process "$@"
