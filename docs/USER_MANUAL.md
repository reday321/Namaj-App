# User Manual

---

## Contents

1. [Getting started](#1-getting-started)
2. [The dashboard](#2-the-dashboard)
3. [The system tray](#3-the-system-tray)
4. [Reminders](#4-reminders)
5. [Prayer Focus Mode](#5-prayer-focus-mode)
6. [Settings](#6-settings)
7. [Ramadan, Friday and silent mode](#7-ramadan-friday-and-silent-mode)
8. [Starting automatically](#8-starting-automatically)
9. [Files and logs](#9-files-and-logs)
10. [Frequently asked questions](#10-frequently-asked-questions)

---

## 1. Getting started

Launch **Salah Guardian** from your application menu, or run `salah-guardian`.

On the very first run it will:

1. Detect your location — GeoClue first, then IP geolocation.
2. Calculate today's and tomorrow's prayer times.
3. Save your coordinates so every later start works **without internet**.
4. Schedule the reminders and settle into the system tray.

**Check the location card first.** If the city is wrong — IP geolocation resolves to
your ISP's exit point, which is often a different city — fix it in
[Settings → Location](#location).

---

## 2. The dashboard

![Dashboard](screenshots/dashboard.png)

| Area | What it shows |
|---|---|
| **Header** | The app name, plus the **Reminders**, **Silent** and **Settings** controls |
| **Next prayer** | The upcoming prayer in English and Arabic, its time, and a live countdown. After Isha it rolls over to tomorrow's Fajr. |
| **Current location** | Where the times are calculated for, the coordinates, and which method found them. **Detect again** re-runs detection. |
| **Date** | Today's Gregorian and Hijri dates. A *Ramadan Mubarak* badge appears during Ramadan. |
| **Current time** | A live clock in your chosen format |
| **Today's prayer times** | All six entries. The upcoming one is highlighted in green; those already passed are dimmed. |
| **Tomorrow's prayer times** | The same for the next day, calculated in advance |
| **Status bar** | The result of your last action, and a reminder that closing the window does not quit |

On Fridays, **Dhuhr is shown and announced as Jumu'ah**.

> **Closing the window does not quit the application.** It keeps running in the tray so
> your reminders continue. Use **Tray → Exit** to quit properly.

---

## 3. The system tray

Right-click the tray icon:

| Item | Action |
|---|---|
| **Open Dashboard** | Shows the main window |
| **Today's Prayer Times** | A submenu listing all six times at a glance |
| **Enable Reminders** | Turns notifications on and off |
| **Enable Prayer Focus Mode** | Turns the fullscreen overlay on and off |
| **Silent Mode** | Mutes everything without changing your other settings |
| **Settings** | Opens the settings window |
| **Exit** | Quits Salah Guardian completely |

Hovering over the icon shows the next prayer and its countdown.

**No tray icon?** GNOME ships without tray support. Install the
*AppIndicator and KStatusNotifierItem Support* extension and log out and back in.
Salah Guardian notices there is no tray and keeps the dashboard open instead, so it
never becomes unreachable.

---

## 4. Reminders

Each of the five daily prayers can produce two notifications:

**Five minutes before** (the lead time is configurable):

> 🕌 **Asr prayer starts in 5 minutes.**
> At 17:05 • العصر

**At the prayer time:**

> 🕌 **It's time for Maghrib.**
> 20:20 • المغرب

The advance warning is a normal notification and fades on its own. The prayer-time one
is *critical*, so on most desktops it stays until you dismiss it.

**There is no audio.** Salah Guardian never plays an adhan or any other sound.

Sunrise is shown on the dashboard because it closes the Fajr window, but it is not a
prayer and is never announced.

If `notify-send` is unavailable, Salah Guardian falls back to a tray balloon, and then
to the log file — so a reminder is never silently lost.

---

## 5. Prayer Focus Mode

![Focus overlay](screenshots/focus-overlay.png)

At prayer time, a fullscreen overlay appears over everything else:

- The prayer name, large, in English and Arabic
- A countdown ring starting at **5:00**
- The prayer time, today's date, the Hijri date and a live clock
- An Islamic geometric backdrop over a dark translucent ground

**It closes when — and only when:**

| | |
|---|---|
| The countdown reaches zero | after 5 minutes by default |
| You press **Close** | dismisses it |
| You press **Skip** | dismisses it and records that you skipped |

**Escape does nothing. Alt+F4 does nothing.** If another window steals focus, the
overlay raises itself again. That is deliberate: the point is a reminder you have to
acknowledge, not one you dismiss by reflex.

It appears only at the prayer time itself, never for the advance warning — the whole
point of the warning is to let you finish what you are doing.

Turn it off in **Settings → Prayer focus mode**, or from the tray menu.

> **On Wayland**, the compositor decides window stacking and may refuse the
> always-on-top request. The overlay still opens fullscreen and still counts down.
> On X11 it is always on top.

---

## 6. Settings

![Settings](screenshots/settings.png)

**Every change is saved the moment you make it.** There is no Save button, and no way
to end up with unsaved edits. The one exception is latitude and longitude, which apply
when you press **Apply coordinates** — half-typed numbers should not be written on
every keystroke.

### Location

| Setting | Meaning |
|---|---|
| **Detect my location automatically** | GeoClue, then IP geolocation. Untick to pin the coordinates below. |
| **Latitude / Longitude** | Decimal degrees. North and east are positive; south and west negative. |
| **City / Country** | Labels only — they do not affect the calculation |
| **Apply coordinates** | Validates and saves, and switches automatic detection off |
| **Detect now** | Re-runs detection immediately |

To find your coordinates, search your city on any map service and copy the two decimal
numbers. Four decimal places is about 11 metres — far more precision than prayer times
need.

### Prayer time calculation

| Setting | Meaning |
|---|---|
| **Method** | The twilight angle convention for Fajr and Isha. Pick the one your local mosque uses. |
| **Madhab** | *Shafi / Maliki / Hanbali* or *Hanafi*. Affects **Asr only** — Hanafi Asr is noticeably later. |
| **High latitude rule** | Only relevant above roughly 48°, where the sun may not reach the twilight angle |
| **Custom Fajr / Isha angle** | Enabled only when Method is *Custom angles* |

Available methods:

| Method | Fajr / Isha | Commonly used in |
|---|---|---|
| Muslim World League | 18° / 17° | Europe, Far East, parts of America |
| ISNA (North America) | 15° / 15° | USA, Canada |
| Umm al-Qura (Makkah) | 18.5° / 90 min after Maghrib | Saudi Arabia |
| Egyptian General Authority | 19.5° / 17.5° | Egypt, Syria, Iraq, Lebanon, Malaysia |
| Karachi | 18° / 18° | Pakistan, Bangladesh, India, Afghanistan |
| **Diyanet (Turkey)** | 18° / 17° with Diyanet offsets | Turkey |
| Dubai (UAE) | 18.2° / 18.2° | United Arab Emirates |
| Qatar | 18° / 90 min after Maghrib | Qatar |
| Kuwait | 18° / 17.5° | Kuwait |
| Singapore (MUIS) | 20° / 18° | Singapore, Malaysia |
| Moonsighting Committee | seasonal | Worldwide |
| Custom angles | yours | Anywhere |

### Reminders

| Setting | Meaning |
|---|---|
| **Enable desktop notifications** | The master switch |
| **Remind me before (minutes)** | The advance warning lead, 0–60. Set **0** to switch it off. |
| **Notify at the prayer time itself** | The second notification |
| **Friday reminder for Jumu'ah** | A Friday morning reminder |
| **Suhoor and iftar reminders during Ramadan** | Automatic during Ramadan |
| **Silent mode** | Mutes everything, keeping all your other settings |

### Prayer focus mode

| Setting | Meaning |
|---|---|
| **Show the fullscreen reminder at prayer time** | Turns the overlay on and off |
| **Overlay duration (seconds)** | 30–3600; the default is 300 (5 minutes) |

### Appearance

| Setting | Meaning |
|---|---|
| **Theme** | *Dark* (default), *Light*, or *Midnight* for night use |
| **Use a 24 hour clock** | `18:42` versus `06:42 PM` |
| **Show the Hijri date** | The Islamic date on the dashboard |

### Startup

| Setting | Meaning |
|---|---|
| **Start Salah Guardian when I log in** | Writes the autostart entry |
| **Start minimised to the system tray** | Applies to automatic launches |

---

## 7. Ramadan, Friday and silent mode

### Ramadan

Detected automatically from the Hijri calendar. During Ramadan you get:

- A **Ramadan Mubarak** badge on the dashboard
- **Suhoor** — 30 minutes before Fajr: *🌙 Suhoor ends in 30 minutes.*
- **Iftar** — at Maghrib: *🌙 Iftar — time to break your fast.*

Turn these off under **Settings → Reminders**.

> The Hijri date comes from the Umm al-Qura calendar bundled with Java. Your local moon
> sighting may differ by a day.

### Friday

On Fridays, Dhuhr is shown and announced as **Jumu'ah**, and a morning reminder at 09:00
(configurable in `config.json` via `fridayReminderHour`) says:

> 🕌 **Jumu'ah is today.**
> Congregational prayer at 13:15. Remember Surah al-Kahf.

### Silent mode

Mutes every reminder — advance warnings, prayer times, Friday and Ramadan — **without**
changing any of your other preferences. Turn it on for a meeting and off afterwards, and
everything returns exactly as it was.

Available from the dashboard header, the tray menu, and Settings.

---

## 8. Starting automatically

Tick **Settings → Startup → Start Salah Guardian when I log in**.

This writes `~/.config/autostart/salah-guardian.desktop`, the standard freedesktop
mechanism honoured by GNOME, KDE Plasma, XFCE, Cinnamon, MATE and LXQt. You can see and
remove it from your desktop's own *Startup Applications* tool.

The entry launches with `--minimised`, so Salah Guardian starts quietly in the tray.

**If the setting will not stay ticked**, the autostart entry needs a real installed
executable. It works from a `.deb`, `.rpm` or `install.sh` installation, but not from
`./gradlew run`. The path that was tried is written to the log.

---

## 9. Files and logs

| Path | Contents |
|---|---|
| `~/.config/salahguardian/config.json` | Every setting, including your coordinates |
| `~/.local/share/salahguardian/logs/` | Rotating logs |
| `~/.config/autostart/salah-guardian.desktop` | Only when "Start on login" is enabled |

Logs rotate daily, roll early past 5 MB, keep 14 days, are gzipped, and never exceed
50 MB in total. Every reminder is logged whether or not it could be displayed, so the
log answers "did it actually fire?".

```bash
tail -f ~/.local/share/salahguardian/logs/salah-guardian.log
```

`config.json` is plain, pretty-printed JSON and safe to edit by hand while the
application is closed. Anything out of range is clamped on load rather than rejected.

To reset everything:

```bash
rm -rf ~/.config/salahguardian ~/.local/share/salahguardian
```

---

## 10. Frequently asked questions

**Does it play the adhan?**
No. Salah Guardian sends desktop notifications only — deliberately, so it is usable in
an office or a shared space.

**Does it work offline?**
Yes, completely, once a location has been stored. Prayer times are computed locally from
astronomical formulas; only the initial IP geolocation lookup needs the internet, and
only if GeoClue is unavailable.

**Does it send my location anywhere?**
Only to the IP geolocation service, and only while it is still trying to find you.
Once your coordinates are saved, no request is ever made again. If you enter your
coordinates manually, no request is made at all.

**Why do the times differ from my mosque's timetable by a minute or two?**
Times are computed for your exact coordinates, whereas a printed timetable is usually
for the city centre, and different authorities use different angles. Match the
**Method** to your mosque, and use `manualAdjustments` in `config.json` for the last
minute or two:

```json
"manualAdjustments": [0, 0, 2, 0, 1, 0]
```

The six values are minutes for Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha.

**Why is Asr later than I expect?**
You are probably on the Hanafi madhab, which places Asr when an object's shadow is twice
its length rather than equal to it. Change it in
**Settings → Prayer time calculation → Madhab**.

**What happens if I suspend my laptop?**
A watchdog notices the clock jump on resume, recalculates and re-arms. A reminder missed
by less than two minutes is still delivered; older ones are dropped rather than arriving
hours late.

**What happens at midnight?**
The watchdog notices the date change, recalculates, and arms the first reminder of the
new day. Tomorrow's times are already computed, so nothing is lost.

**I live in the far north — why does the dashboard say "approximate times"?**
Above roughly 48° there are days when the sun never crosses the twilight angles, so
Fajr, Maghrib and Isha have no astronomical definition. Salah Guardian then uses the
*nearest latitude* (`aqrab al-bilad`) convention at 45° and tells you it has done so,
rather than showing you an empty timetable.

**Can I change the theme?**
**Settings → Appearance → Theme**: *Dark*, *Light* or *Midnight*. It applies at once, to
every open window.

**How do I quit completely?**
**Tray → Exit**. Closing the dashboard window only hides it.
