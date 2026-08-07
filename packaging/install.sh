#!/usr/bin/env bash
#
# Salah Guardian - portable installer for the jpackage app-image.
#
# Use this on distributions where neither the .deb nor the .rpm applies (Arch,
# openSUSE Tumbleweed, NixOS with FHS, Slackware, ...), or when you simply want
# an installation you can remove by deleting one directory.
#
# Installs to:
#   /opt/salah-guardian                        the runtime image
#   /usr/local/bin/salah-guardian             a launcher on the PATH
#   /usr/share/applications/                  the desktop entry
#   /usr/share/icons/hicolor/<size>/apps/     the icon set
#
# Run with --user to install into $HOME instead, with no root required.
#
set -euo pipefail

APP_NAME="SalahGuardian"
PKG_NAME="salah-guardian"
IMAGE_DIR="${IMAGE_DIR:-build/jpackage/${APP_NAME}}"
ICON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/icons"

USER_MODE=0
[[ "${1:-}" == "--user" ]] && USER_MODE=1

if [[ ! -d "$IMAGE_DIR" ]]; then
    echo "error: no app-image at '$IMAGE_DIR'." >&2
    echo "       Build it first:  ./gradlew jpackageAppImage" >&2
    echo "       Or point IMAGE_DIR at an unpacked image." >&2
    exit 1
fi

if (( USER_MODE )); then
    PREFIX="$HOME/.local"
    OPT_DIR="$PREFIX/lib/salah-guardian"
    BIN_DIR="$PREFIX/bin"
    APP_DIR="$PREFIX/share/applications"
    ICONS_ROOT="$PREFIX/share/icons/hicolor"
    SUDO=""
else
    OPT_DIR="/opt/salah-guardian"
    BIN_DIR="/usr/local/bin"
    APP_DIR="/usr/share/applications"
    ICONS_ROOT="/usr/share/icons/hicolor"
    SUDO="sudo"
    if [[ $EUID -eq 0 ]]; then SUDO=""; fi
fi

echo "==> Installing Salah Guardian to $OPT_DIR"
$SUDO rm -rf "$OPT_DIR"
$SUDO mkdir -p "$OPT_DIR"
$SUDO cp -a "$IMAGE_DIR/." "$OPT_DIR/"

echo "==> Creating the launcher $BIN_DIR/$PKG_NAME"
$SUDO mkdir -p "$BIN_DIR"
$SUDO tee "$BIN_DIR/$PKG_NAME" >/dev/null <<LAUNCHER
#!/bin/sh
exec "$OPT_DIR/bin/$APP_NAME" "\$@"
LAUNCHER
$SUDO chmod +x "$BIN_DIR/$PKG_NAME"

echo "==> Installing the icon set"
for size in 16 24 32 48 64 128 256 512; do
    src="$ICON_DIR/${PKG_NAME}-${size}.png"
    [[ -f "$src" ]] || continue
    dest="$ICONS_ROOT/${size}x${size}/apps"
    $SUDO mkdir -p "$dest"
    $SUDO cp "$src" "$dest/${PKG_NAME}.png"
done

echo "==> Installing the desktop entry"
$SUDO mkdir -p "$APP_DIR"
$SUDO tee "$APP_DIR/${PKG_NAME}.desktop" >/dev/null <<DESKTOP
[Desktop Entry]
Type=Application
Version=1.0
Name=Salah Guardian
GenericName=Prayer Times
Comment=Reminds you of prayer times based on your location
Exec=$OPT_DIR/bin/$APP_NAME
Icon=$PKG_NAME
Terminal=false
Categories=Utility;Clock;
Keywords=prayer;salah;namaz;adhan;islam;qibla;muslim;
StartupNotify=true
DESKTOP

# Refresh the desktop caches; harmless when the tools are absent.
command -v update-desktop-database >/dev/null 2>&1 && \
    $SUDO update-desktop-database "$APP_DIR" || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && \
    $SUDO gtk-update-icon-cache -f -t "$ICONS_ROOT" >/dev/null 2>&1 || true

echo
echo "Salah Guardian is installed."
echo "  Launch from your application menu, or run: $PKG_NAME"
echo "  Enable 'Start on login' in Settings to have it start automatically."
