#!/usr/bin/env bash
#
# Builds a double-clickable macOS application bundle that contains a Ghidra
# distribution and a PyGhidra virtual environment, and launches Ghidra through
# PyGhidra.
#
# Usage: make_ghidra_app.sh [ghidra_dist.zip] [destination.app]
#
#   ghidra_dist.zip  defaults to the newest build/dist/*.zip of this repository
#   destination.app  defaults to /Applications/Ghidra.app
#
# The bundle still uses the installed JDK and a Python 3.13 interpreter
# (set PYTHON to use a different one).  Python virtual environments cannot be
# moved, so the environment is created at its final location; rerun this
# script if the application is moved.
#
# The icon is compiled from Ghidra.icon (an Icon Composer icon) and needs Xcode.
# Its artwork is generated from the Ghidra logo by make_icon_artwork.swift.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

ZIP="${1:-$(ls -t "$REPO_DIR"/build/dist/ghidra_*.zip 2>/dev/null | head -n 1)}"
APP="${2:-/Applications/Ghidra.app}"
PYTHON="${PYTHON:-python3.13}"
MARKER="Contents/Resources/.made-by-make_ghidra_app"

die() {
	echo "error: $*" >&2
	exit 1
}

[ -n "$ZIP" ] && [ -f "$ZIP" ] || die "no Ghidra distribution zip found; run 'gradle buildGhidra' first"
command -v "$PYTHON" >/dev/null || die "'$PYTHON' not found; install it or set PYTHON"
xcrun --find actool >/dev/null 2>&1 || die "actool not found; install Xcode"
[[ "$APP" == *.app ]] || die "destination must end in .app: $APP"

if [ -e "$APP" ]; then
	# never delete an application this script did not create
	[ -f "$APP/$MARKER" ] || die "$APP exists and was not created by this script"
	if pgrep -f "$APP/Contents/" >/dev/null; then
		die "Ghidra is running from $APP; quit it first"
	fi
	echo "Removing previous $APP"
	rm -rf "$APP"
fi

RES="$APP/Contents/Resources"
mkdir -p "$APP/Contents/MacOS" "$RES"
touch "$APP/$MARKER"

echo "Unpacking $(basename "$ZIP")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ZIP" -d "$TMP"
TOP=("$TMP"/ghidra_*)
[ ${#TOP[@]} -eq 1 ] && [ -d "${TOP[0]}" ] || die "unexpected layout in $ZIP"
mv "${TOP[0]}" "$RES/ghidra"
GHIDRA_VERSION="$(sed -n 's/^application.version=//p' "$RES/ghidra/Ghidra/application.properties")"

echo "Creating PyGhidra environment with $("$PYTHON" --version)"
"$PYTHON" -m venv "$RES/venv"
"$RES/venv/bin/python3" -m pip install --quiet --disable-pip-version-check --no-index \
	-f "$RES/ghidra/Ghidra/Features/PyGhidra/pypkg/dist" pyghidra

echo "Compiling icon"
# Produces Assets.car with the layered icon, plus Ghidra.icns for older macOS versions
xcrun actool "$SCRIPT_DIR/Ghidra.icon" --compile "$RES" --platform macosx \
	--minimum-deployment-target 11.0 --app-icon Ghidra \
	--output-partial-info-plist "$TMP/icon.plist" --errors --warnings >/dev/null
[ -f "$RES/Assets.car" ] && [ -f "$RES/Ghidra.icns" ] || die "actool did not produce the icon"

cat > "$APP/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleName</key>
	<string>Ghidra</string>
	<key>CFBundleDisplayName</key>
	<string>Ghidra</string>
	<key>CFBundleIdentifier</key>
	<string>local.ghidra.app</string>
	<key>CFBundleExecutable</key>
	<string>Ghidra</string>
	<key>CFBundleIconFile</key>
	<string>Ghidra</string>
	<key>CFBundleIconName</key>
	<string>Ghidra</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleShortVersionString</key>
	<string>${GHIDRA_VERSION}</string>
	<key>CFBundleVersion</key>
	<string>${GHIDRA_VERSION}</string>
	<key>LSMinimumSystemVersion</key>
	<string>11.0</string>
</dict>
</plist>
EOF

cat > "$APP/Contents/MacOS/Ghidra" <<'EOF'
#!/bin/bash
# Launches the Ghidra distribution in this bundle through PyGhidra, using the
# bundled PyGhidra virtual environment.

RES="$(cd "$(dirname "$0")/../Resources" && pwd)"
LOG="$HOME/Library/Logs/Ghidra.log"

# Apps started from Finder get a minimal PATH.
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

fail() {
	osascript -e 'on run argv' \
		-e 'display alert "Ghidra could not start" message (item 1 of argv) as critical' \
		-e 'end run' "$1" >/dev/null 2>&1
	exit 1
}

[ -x "$RES/venv/bin/python3" ] || fail "The PyGhidra environment in $RES/venv is missing or broken. Rebuild the app with make_ghidra_app.sh."

echo "=== $(date) ===" >> "$LOG"

# The launcher uses the virtual environment it runs in.  It asks before
# upgrading PyGhidra; answer yes, since there is no terminal to type into.
# --console keeps this process alive while Ghidra runs: macOS terminates the
# processes an app started once the app itself exits.
printf 'y\ny\ny\n' | "$RES/venv/bin/python3" \
	"$RES/ghidra/Ghidra/Features/PyGhidra/support/pyghidra_launcher.py" \
	"$RES/ghidra" --console >> "$LOG" 2>&1
status=$?

if [ $status -ne 0 ]; then
	fail "The PyGhidra launcher exited with status $status.

$(tail -n 8 "$LOG")

Full log: $LOG"
fi
EOF
chmod +x "$APP/Contents/MacOS/Ghidra"

echo "Created $APP (Ghidra $GHIDRA_VERSION)"
