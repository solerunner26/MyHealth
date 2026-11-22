#!/usr/bin/env bash
# Exports the local toolchain installed by tools/bootstrap-env.sh. Source it, do not execute it.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${ROOT}/.toolchain"
export JAVA_HOME="${TC}/jdk"
export ANDROID_HOME="${TC}/sdk"
export ANDROID_SDK_ROOT="${TC}/sdk"
export GRADLE_USER_HOME="${TC}/gradle-home"
export PATH="${JAVA_HOME}/bin:${TC}/gradle-9.9.2/bin:${PATH}"
if [ -f "${TC}/truststore.jks" ]; then
  export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=${TC}/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
fi
