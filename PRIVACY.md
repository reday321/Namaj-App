# Privacy

Salah Guardian is a prayer reminder. The data it handles — where you are, and
that you pray — is sensitive, and this document states plainly what happens to it.

## The short version

- **One network request exists**, and it only happens if you agree to it.
- **Nothing is collected, tracked or transmitted** to us. There is no analytics,
  no telemetry, no crash reporting, no identifier of any kind.
- **Your location never leaves your machine.** Not even to us — there is no "us"
  in the data path.
- After your location is known once, the application works **entirely offline**.

## The one request

To calculate prayer times the application needs your coordinates. It tries, in order:

1. **GeoClue**, your desktop's own location service. Local; nothing leaves the machine.
2. **IP geolocation** — the only outbound request. Skipped unless you agree.
3. **Whatever you last saved**, or coordinates you typed yourself.

If step 2 is reached you are asked first, before anything is sent. Decline and the
application stays fully offline; you enter your coordinates in Settings instead.

### What that request discloses

An HTTPS request to `ipapi.co` or `ipwho.is`. Those services see **your public IP
address** — as they do for any site you visit — and return an approximate city.

The request carries a generic `User-Agent` that does **not** identify this
application. That is deliberate: naming a prayer application in the request would
disclose your religion to a commercial data broker alongside your IP address.

Once a location is stored, **the request is never made again**.

## What is stored, and where

| Path | Contents | Permissions |
|---|---|---|
| `~/.config/salahguardian/config.json` | Coordinates, language, calculation method, preferences | `0600` — owner only |
| `~/.local/share/salahguardian/logs/` | Diagnostic logs, rotated over 14 days | `0700` directory, `0600` files |

Both honour `XDG_CONFIG_HOME` and `XDG_DATA_HOME`.

**Logs never contain your precise coordinates.** They record a value rounded to
roughly eleven kilometres — enough to diagnose whether detection worked, not enough
to identify where you live. The exact value appears only if you deliberately enable
debug logging.

## What is never done

- ❌ No analytics, telemetry, usage statistics or crash reporting
- ❌ No advertising identifiers, device fingerprinting or persistent IDs
- ❌ No account, sign-in or cloud sync
- ❌ No sharing of your location with anyone, including us
- ❌ No background network activity of any kind after setup

## Your control

| To do this | Go here |
|---|---|
| Never touch the network at all | Decline at first launch, or Settings → Location → untick *Detect automatically* |
| Change or correct your location | Settings → Location → enter coordinates → Apply |
| Delete everything | `rm -rf ~/.config/salahguardian ~/.local/share/salahguardian` |
| Inspect what is stored | The config file is plain, readable JSON |

## Legal basis

Where GDPR applies: coordinates combined with the nature of this application can
imply religious belief, which is special-category data under **Article 9**. The
lawful basis for the single outbound request is **explicit consent** (Article 9(2)(a)),
obtained before the request is made and revocable at any time in Settings. No data
is transferred to us or to any processor acting for us.

## Reporting a problem

Security or privacy issues: open an issue at
<https://github.com/reday321/Namaj-App/issues>.
