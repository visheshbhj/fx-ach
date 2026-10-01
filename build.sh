#!/usr/bin/env bash
# Builds ACH Studio.
#
#   ./build.sh                 runnable jar           -> target/ach-studio.jar   (needs Java 21+ to run)
#   ./build.sh app             self-contained app     -> dist/ach-studio/        (bundles its own Java runtime)
#   ./build.sh installer       native installer       -> dist/                   (.deb/.rpm on Linux, .dmg on macOS, .msi on Windows)
#
# Options:
#   --skip-tests               skip the unit tests
#
# The jar contains JavaFX for the platform it was built on, so build on each OS you want to ship for.
set -euo pipefail

cd "$(dirname "$0")"

TARGET="jar"
MVN_ARGS=(-B clean package)
for arg in "$@"; do
    case "$arg" in
        jar|app|installer) TARGET="$arg" ;;
        --skip-tests) MVN_ARGS+=(-DskipTests) ;;
        -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown argument: $arg (try --help)" >&2; exit 1 ;;
    esac
done

NAME="ach-studio"
JAR="target/$NAME.jar"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
APP_VERSION="${VERSION%%-*}"   # jpackage needs a plain numeric version (1.0-SNAPSHOT -> 1.0)

echo "==> Building $NAME $VERSION ($TARGET)"
./mvnw "${MVN_ARGS[@]}"

if [[ ! -f "$JAR" ]]; then
    echo "Build did not produce $JAR" >&2
    exit 1
fi

if [[ "$TARGET" == "jar" ]]; then
    echo
    echo "Done: $JAR"
    echo "Run it with:  java -jar $JAR [file.ach ...]"
    exit 0
fi

if ! command -v jpackage >/dev/null 2>&1; then
    echo "jpackage not found. It ships with JDK 14+; put the JDK's bin directory on PATH." >&2
    exit 1
fi

# jpackage bundles everything in --input; give it just the fat jar.
INPUT="target/jpackage-input"
rm -rf "$INPUT"
mkdir -p "$INPUT" dist
cp "$JAR" "$INPUT/"

# Java modules JavaFX and the app need when run from the classpath.
MODULES="java.base,java.desktop,java.logging,java.prefs,java.scripting,java.xml,jdk.unsupported,jdk.charsets"

COMMON=(
    --name "$NAME"
    --app-version "$APP_VERSION"
    --vendor "ACH Studio"
    --description "Read, build and present NACHA ACH files"
    --input "$INPUT"
    --main-jar "$NAME.jar"
    --main-class com.fx.ach.Launcher
    --add-modules "$MODULES"
    --jlink-options "--strip-debug --no-man-pages --no-header-files"
    --dest dist
)

if [[ "$TARGET" == "app" ]]; then
    rm -rf "dist/$NAME"
    jpackage --type app-image "${COMMON[@]}"
    echo
    case "$(uname -s)" in
        Darwin) echo "Done: dist/$NAME.app" ;;
        MINGW*|MSYS*|CYGWIN*) echo "Done: dist/$NAME/$NAME.exe" ;;
        *) echo "Done: dist/$NAME/bin/$NAME" ;;
    esac
    exit 0
fi

# installer
case "$(uname -s)" in
    Linux)
        if command -v dpkg-deb >/dev/null 2>&1; then TYPE=deb
        elif command -v rpmbuild >/dev/null 2>&1; then TYPE=rpm
        else echo "Need dpkg-deb (for .deb) or rpmbuild (for .rpm) to build a Linux installer." >&2; exit 1
        fi
        EXTRA=(--linux-shortcut --linux-menu-group Office) ;;
    Darwin) TYPE=dmg; EXTRA=() ;;
    MINGW*|MSYS*|CYGWIN*) TYPE=msi; EXTRA=(--win-menu --win-shortcut --win-dir-chooser) ;;  # needs WiX Toolset
    *) echo "Unsupported OS for installers: $(uname -s)" >&2; exit 1 ;;
esac
jpackage --type "$TYPE" "${COMMON[@]}" "${EXTRA[@]}"
echo
echo "Done: $(ls -t dist/*."$TYPE" | head -1)"
