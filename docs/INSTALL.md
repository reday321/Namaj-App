# Installation Guide

Salah Guardian ships as a self-contained package: it bundles its own Java runtime, so
**you do not need Java installed** to run it.

---

## 1. Choose a package

| Your distribution | Package | Command |
|---|---|---|
| Ubuntu, Linux Mint, Debian, Pop!\_OS, elementary, Zorin, Kali | `.deb` | `sudo apt install ./salah-guardian_1.0.0_amd64.deb` |
| Fedora, RHEL, CentOS Stream, AlmaLinux, Rocky | `.rpm` | `sudo dnf install ./salah-guardian-1.0.0-1.x86_64.rpm` |
| openSUSE Leap / Tumbleweed | `.rpm` | `sudo zypper install ./salah-guardian-1.0.0-1.x86_64.rpm` |
| Arch, Manjaro, EndeavourOS | app-image | `./packaging/install.sh` |
| NixOS, Slackware, Gentoo, anything else | app-image | `./packaging/install.sh` |
| Any distribution, without root | app-image | `./packaging/install.sh --user` |

If you do not have a prebuilt package, build one first — see
[BUILD.md](BUILD.md), or just run:

```bash
./gradlew jpackageDeb      # or jpackageRpm, or jpackageAppImage
ls build/jpackage/
```

---

## 2. Install

### Debian / Ubuntu / Mint

```bash
sudo apt install ./salah-guardian_1.0.0_amd64.deb
```

Use `apt` rather than `dpkg -i` so that dependencies are resolved automatically.

### Fedora / RHEL / openSUSE

```bash
sudo dnf install ./salah-guardian-1.0.0-1.x86_64.rpm
# or
sudo zypper install ./salah-guardian-1.0.0-1.x86_64.rpm
```

### Portable app-image (any distribution)

```bash
./gradlew jpackageAppImage
./packaging/install.sh
```

This installs to `/opt/salah-guardian`, adds `/usr/local/bin/salah-guardian`, and
registers the desktop entry and icons.

**Without root:**

```bash
./packaging/install.sh --user
```

Installs into `~/.local` instead. Make sure `~/.local/bin` is on your `PATH`.

You can also skip installing entirely and run the image in place:

```bash
build/jpackage/SalahGuardian/bin/SalahGuardian
```

---

## 3. Dependencies

### Required

**None.** The package includes a trimmed Java 21 runtime with JavaFX.

### Recommended: `notify-send`

Desktop notifications are delivered through libnotify. Almost every desktop install
already has it; a minimal or server install may not.

```bash
# Debian, Ubuntu, Mint
sudo apt install libnotify-bin

# Fedora, RHEL
sudo dnf install libnotify

# openSUSE
sudo zypper install libnotify-tools

# Arch
sudo pacman -S libnotify
```

Check it works:

```bash
notify-send "Salah Guardian" "Notifications are working"
```

If `notify-send` is missing, Salah Guardian automatically falls back to a system tray
balloon, and then to the log file. **Reminders are never silently lost.**

### Optional: GeoClue

For precise, offline-capable location detection:

```bash
sudo apt install geoclue-2.0      # Debian / Ubuntu / Mint
sudo dnf install geoclue2         # Fedora
sudo pacman -S geoclue            # Arch
```

Without GeoClue, Salah Guardian falls back to IP geolocation, which needs the internet
exactly once. You can also just type your coordinates in Settings.

---

## 4. First run

1. Launch **Salah Guardian** from your application menu, or run `salah-guardian`.
2. It detects your location automatically and shows today's prayer times.
3. Confirm the location on the dashboard. If it is wrong, open
   **Settings → Location**, enter your coordinates, and press **Apply coordinates**.
4. Choose your **calculation method** and **madhab** under
   **Settings → Prayer time calculation** to match your local mosque.
5. Tick **Settings → Startup → Start Salah Guardian when I log in**.

Closing the dashboard window leaves the application running in the system tray.
Use **Tray → Exit** to quit completely.

---

## 5. Troubleshooting

### No tray icon appears

GNOME removed tray support from the shell. Install the AppIndicator extension:

```bash
sudo apt install gnome-shell-extension-appindicator   # Debian / Ubuntu / Mint
sudo dnf install gnome-shell-extension-appindicator   # Fedora
```

Then enable it in the *Extensions* app and log out and back in.

Salah Guardian detects a missing tray and keeps the dashboard window open instead,
so it never becomes unreachable.

### No notifications

1. Check libnotify is installed: `notify-send test test`
2. Check **Silent mode** is off — in the tray menu and in the dashboard header.
3. Check **Settings → Reminders → Enable desktop notifications** is ticked.
4. Check your desktop's Do Not Disturb setting.
5. Look in `~/.local/share/salahguardian/logs/salah-guardian.log` — every reminder is
   logged whether or not it could be displayed.

### The location is wrong

IP geolocation resolves to your ISP's exit point, which can be a different city.
Enter your coordinates manually in **Settings → Location** and press
**Apply coordinates**; that also switches automatic detection off so your value sticks.

### Prayer times are a few minutes off

Different authorities use different twilight angles. Pick the convention your local
mosque uses in **Settings → Prayer time calculation → Method**. For finer control, edit
`manualAdjustments` in `~/.config/salahguardian/config.json` — six values in minutes,
ordered Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha:

```json
"manualAdjustments": [0, 0, 2, 0, 1, 0]
```

### The focus overlay is not on top (Wayland)

Wayland compositors decide window stacking themselves and may refuse a programmatic
always-on-top request. The overlay still opens fullscreen and still counts down.
On X11 it is always on top.

### "Start on login" will not enable

The autostart entry has to point at a real executable. This works from an installed
`.deb`, `.rpm` or `install.sh` package, but not from `./gradlew run`. The exact path
that was tried is written to the log.

### Reset everything

```bash
rm -rf ~/.config/salahguardian ~/.local/share/salahguardian
rm -f  ~/.config/autostart/salah-guardian.desktop
```

---

## 6. Uninstall

```bash
sudo apt remove salah-guardian          # Debian / Ubuntu / Mint
sudo dnf remove salah-guardian          # Fedora
./packaging/uninstall.sh                # app-image install
./packaging/uninstall.sh --user         # user-level app-image install
```

Add `--purge` to `uninstall.sh`, or delete `~/.config/salahguardian` and
`~/.local/share/salahguardian` by hand, to remove your settings and logs too.
