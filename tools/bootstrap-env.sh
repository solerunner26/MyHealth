#!/usr/bin/env bash
#
# Installs a self-contained Android toolchain under .toolchain/ so the project can be built headlessly
# in a fresh sandbox (no Android Studio, no pre-installed SDK).
#
#   tools/bootstrap-env.sh          # install everything (about 3 GB, mostly Gradle caches)
#   source tools/env.sh             # export JAVA_HOME / ANDROID_HOME for this shell
#   ./gradlew :app:assembleDebug
#
# Pinned versions intentionally match gradle/libs.versions.toml and the AGP requirement it implies.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${ROOT}/.toolchain"
JDK_VERSION="${JDK_VERSION:-21}"
GRADLE_VERSION="${GRADLE_VERSION:-9.9.2}"
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip}"
SDK_PACKAGES=("platform-tools" "platforms;android-37.2" "build-tools;37.0.0")

mkdir -p "${TC}"
cd "${TC}"

if [ ! -x "${TC}/jdk/bin/java" ]; then
  echo "==> JDK ${JDK_VERSION}"
  curl -fL --retry 3 -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk && tar xzf jdk.tar.gz -C jdk --strip-components=1 && rm jdk.tar.gz
fi

if [ ! -d "${TC}/gradle-${GRADLE_VERSION}" ]; then
  echo "==> Gradle ${GRADLE_VERSION}"
  curl -fL --retry 3 -o gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q gradle.zip && rm gradle.zip
fi

if [ ! -x "${TC}/sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Android command-line tools"
  curl -fL --retry 3 -o cmdline-tools.zip "${CMDLINE_TOOLS_URL}"
  rm -rf cmdline sdk/cmdline-tools
  unzip -q cmdline-tools.zip -d cmdline
  mkdir -p sdk/cmdline-tools
  mv cmdline/cmdline-tools sdk/cmdline-tools/latest
  rm -rf cmdline cmdline-tools.zip
fi

export JAVA_HOME="${TC}/jdk"
export ANDROID_HOME="${TC}/sdk"
export ANDROID_SDK_ROOT="${TC}/sdk"

# Behind a TLS-inspecting proxy the JVM needs the proxy CA in its truststore. The CA is only used when
# it exists, so the script is equally valid on a normal network.
if [ -f /etc/mitm/ca.crt ] && [ ! -f "${TC}/truststore.jks" ]; then
  cp "${JAVA_HOME}/lib/security/cacerts" "${TC}/truststore.jks"
  "${JAVA_HOME}/bin/keytool" -importcert -noprompt -alias mitm -file /etc/mitm/ca.crt \
    -keystore "${TC}/truststore.jks" -storepass changeit > /dev/null
fi
if [ -f "${TC}/truststore.jks" ]; then
  export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=${TC}/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
fi

SDKMANAGER="${TC}/sdk/cmdline-tools/latest/bin/sdkmanager"
yes | "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" --licenses > /dev/null
"${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" "${SDK_PACKAGES[@]}"

printf 'sdk.dir=%s\n' "${ANDROID_HOME}" > "${ROOT}/local.properties"

echo
echo "Ready. Now run:"
echo "  source tools/env.sh"
echo "  ./gradlew :app:assembleDebug"
