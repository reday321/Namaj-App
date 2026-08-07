# Publishing to the Snap Store

This is the checklist for getting Salah Guardian into the Ubuntu App Center.

---

## 0. Prerequisites

```bash
sudo snap install snapcraft --classic
sudo snap install lxd
sudo lxd init --auto
sudo usermod -aG lxd "$USER"    # then log out and back in
```

`snapcraft` builds inside an LXD container, so the build does not depend on
what happens to be installed on your machine.

---

## 1. Make the repository public

**Do this first.** The store listing embeds screenshot URLs and links to the
source, and the store's crawler must be able to reach them. While the repository
is private, `appstreamcli validate` reports eight `url-not-reachable` warnings
and the App Center listing will show no screenshots.

```bash
gh repo edit reday321/Namaj-App --visibility public --accept-visibility-change-consequences
```

Structural validation already passes:

```bash
appstreamcli validate --no-net packaging/salah-guardian.metainfo.xml
# ✔ Validation was successful
```

---

## 2. Register the name

```bash
snapcraft login          # uses your Ubuntu One account
snapcraft register salah-guardian
```

The name was free at the time of writing. Registering does not publish anything.

---

## 3. Build

```bash
snapcraft            # produces salah-guardian_1.0.0_amd64.snap
```

Test it locally before uploading. `--dangerous` is required because a locally
built snap is unsigned:

```bash
sudo snap install --dangerous ./salah-guardian_1.0.0_amd64.snap
```

### Connect the reviewed interfaces by hand while testing

Neither of these connects automatically, so on a local install you must do it
yourself — this is also exactly what your users would have to do if the review
request is refused:

```bash
sudo snap connect salah-guardian:location-observe
sudo snap connect salah-guardian:login-session-control
snap connections salah-guardian     # verify
```

Check that it behaves without them too, since that is the state a user sees
before connecting: location detection should fall back to the IP lookup or
manual entry, and the screen-lock setting should appear greyed out.

---

## 4. Upload and request the interfaces

```bash
snapcraft upload --release=edge salah-guardian_1.0.0_amd64.snap
```

The automated review will **hold** the upload, because two interfaces need human
approval. Reply in the review thread on <https://dashboard.snapcraft.io> with a
justification. Suggested text:

> **`location-observe`**
> Salah Guardian calculates Islamic prayer times, which depend entirely on the
> user's latitude and longitude — the times are wrong at any other location.
> It reads the position once from GeoClue2, at start-up, and caches it; there is
> no tracking, no logging of precise coordinates and no transmission of the
> position anywhere. Without this interface the only alternatives are an
> internet IP lookup, which is less accurate and requires network access, or
> asking the user to type coordinates by hand. Location handling is documented
> in PRIVACY.md.

> **`login-session-control`**
> The application offers an optional, off-by-default setting that locks the
> desktop session at prayer time, so the user actually steps away from their
> machine. It calls `loginctl lock-session` on the user's own session and
> nothing else — it never terminates a session, suspends, or powers off. The
> lock is always preceded by a visible countdown and a "Don't lock" button, and
> the setting is disabled entirely when no lock mechanism is available. If this
> interface is not granted the feature simply stays unavailable and the rest of
> the application is unaffected.

Reviews typically take a few days. `login-session-control` is the one most likely
to be questioned; if it is refused, the feature degrades cleanly and the snap can
ship without it.

---

## 5. Promote to stable

Once it works from `edge`:

```bash
snapcraft release salah-guardian <revision> stable
```

It appears in the Ubuntu App Center shortly afterwards.

---

## What the confinement means for the application

| Behaviour | Under strict confinement |
|---|---|
| Settings and logs | Move to `~/snap/salah-guardian/current/` — `ConfigPaths` honours `XDG_*`, so nothing in the code changes |
| `notify-send` | Shipped in the snap (`libnotify-bin`); delivery goes through the desktop portal |
| `gdbus` / GeoClue | Shipped (`libglib2.0-bin`); needs `location-observe` connected |
| `loginctl` | Shipped (`systemd`); needs `login-session-control` connected |
| Start on login | Works through the `autostart` interface |
| System tray | Works through `unity7` |
| Binary resolution | `LinuxScreenLockService` trusts `$SNAP/usr/bin` as well as `/usr/bin` |

## Known limitation

The snap sees its own `XDG_CONFIG_HOME`, so an existing `.deb` installation's
settings are **not** picked up. To carry them over:

```bash
cp ~/.config/salahguardian/config.json \
   ~/snap/salah-guardian/current/.config/salahguardian/config.json
```
