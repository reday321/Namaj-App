#!/usr/bin/env bash
#
# Salah Guardian - removes an installation created by install.sh.
#
# User data is preserved by default. Pass --purge to delete the configuration
# in ~/.config/salahguardian and the logs in ~/.local/share/salahguardian too.
#
set -euo pipefail

PKG_NAME="salah-guardian"
PURGE=0
USER_MODE=0
for arg in "$@"; do
    case "$arg" in
        --purge) PURGE=1 ;;
        --user)  USER_MODE=1 ;;
        *) echo "unknown option: $arg" >&2; exit 1 ;;
    esac
done

if (( USER_MODE )); then
    PREFIX="$HOME/.local"
    TARGETS=("$PREFIX/lib/salah-guardian" "$PREFIX/bin/$PKG_NAME"
             "$PREFIX/share/applications/${PKG_NAME}.desktop")
    ICONS_ROOT="$PREFIX/share/icons/hicolor"
    SUDO=""
else
    TARGETS=("/opt/salah-guardian" "/usr/local/bin/$PKG_NAME"
             "/usr/share/applications/${PKG_NAME}.desktop")
    ICONS_ROOT="/usr/share/icons/hicolor"
    SUDO="sudo"
    if [[ $EUID -eq 0 ]]; then SUDO=""; fi
fi

echo "==> Removing Salah Guardian"
for target in "${TARGETS[@]}"; do
    [[ -e "$target" ]] && $SUDO rm -rf "$target" && echo "    removed $target"
done

for size in 16 24 32 48 64 128 256 512; do
    icon="$ICONS_ROOT/${size}x${size}/apps/${PKG_NAME}.png"
    [[ -e "$icon" ]] && $SUDO rm -f "$icon"
done

# The autostart entry belongs to the user, not to the package.
rm -f "$HOME/.config/autostart/${PKG_NAME}.desktop" 2>/dev/null || true

if (( PURGE )); then
    echo "==> Purging user data"
    rm -rf "$HOME/.config/salahguardian" "$HOME/.local/share/salahguardian"
else
    echo "    Kept your settings in ~/.config/salahguardian (use --purge to delete)."
fi

echo "Done."
