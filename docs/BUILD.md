# Build Instructions

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| **JDK** | 21 | A full JDK, not a JRE — `jpackage` and `jlink` are needed for packaging |
| **Gradle** | — | Not required; use the bundled wrapper (`./gradlew`) |
| **Internet** | first build only | Gradle downloads dependencies once, then caches them |

### Install a JDK 21

```bash
# Debian, Ubuntu 24.04+, Mint 22+
sudo apt install openjdk-21-jdk

# Ubuntu 22.04 (21 is not in the default archive)
sudo add-apt-repository ppa:openjdk-r/ppa && sudo apt update
sudo apt install openjdk-21-jdk

# Fedora, RHEL
sudo dnf install java-21-openjdk-devel

# openSUSE
sudo zypper install java-21-openjdk-devel

# Arch
sudo pacman -S jdk21-openjdk
```

Verify you have the **JDK**, not just the runtime:

```bash
javac -version      # must print 21.x - if this fails you have a JRE
jpackage --version  # must print 21.x - required for packaging
```

> **If your distribution only ships an older JDK**, you do not have to install anything.
> `settings.gradle` applies the Foojay toolchain resolver, so Gradle downloads a matching
> JDK 21 by itself on the first build. Packaging still needs a local JDK 21, because
> `jpackage` builds a native runtime image.

To point the build at a JDK in a non-standard location:

```bash
./gradlew build -Porg.gradle.java.installations.paths=/path/to/jdk-21
```

### Optional: build-time libraries

`.deb` and `.rpm` generation shells out to the distribution's own tools:

```bash
sudo apt install fakeroot binutils    # for jpackageDeb
sudo dnf install rpm-build            # for jpackageRpm
```

---

## 2. Build

```bash
git clone <repository-url> salah-guardian
cd salah-guardian

./gradlew build       # compiles, runs the tests, assembles the jar
```

On the first run Gradle downloads itself, the plugins and the dependencies. Later
builds are incremental and take a couple of seconds.

---

## 3. Gradle tasks

| Task | What it does |
|---|---|
| `./gradlew build` | Compile, test and assemble everything |
| `./gradlew test` | Run the unit test suite (155 tests, no display required) |
| `./gradlew run` | Launch the application from source |
| `./gradlew installDist` | Stage a runnable image under `build/install/salah-guardian` |
| `./gradlew jpackageAppImage` | Portable self-contained folder → `build/jpackage/SalahGuardian` |
| `./gradlew jpackageDeb` | `.deb` for Debian, Ubuntu, Mint |
| `./gradlew jpackageRpm` | `.rpm` for Fedora, RHEL, openSUSE |
| `./gradlew packageAll` | All three package formats |
| `./gradlew javadoc` | API documentation → `build/docs/javadoc` |
| `./gradlew jacocoTestReport` | Coverage report → `build/reports/jacoco` |
| `./gradlew clean` | Delete `build/` |

Every packaging task depends on `installDist`, so a single command is enough:

```bash
./gradlew jpackageDeb
ls -lh build/jpackage/
```

---

## 4. Running during development

```bash
./gradlew run

# start straight into the tray, as the autostart entry does
./gradlew run --args="--minimised"

# open the settings window on start
./gradlew run --args="--settings"

# verbose logging
./gradlew run -Dorg.gradle.jvmargs="-Dsalahguardian.debug=true"
```

Logs go to `~/.local/share/salahguardian/logs/salah-guardian.log` in every case.

---

## 5. Project layout

```
salah-guardian/
├── build.gradle                 build, dependencies, jpackage tasks
├── settings.gradle              project name + Foojay toolchain resolver
├── gradle.properties            versions and application metadata
├── gradlew, gradle/wrapper/     Gradle wrapper (no local Gradle needed)
├── src/main/java/               application source
├── src/main/resources/
│   ├── css/                     base.css + dark / light / midnight / focus-overlay
│   ├── icons/                   application and tray icons
│   └── logback.xml              rotating file logging
├── src/test/java/               unit tests
├── packaging/
│   ├── icons/                   hicolor icon set, 16px to 512px
│   ├── salah-guardian.desktop   canonical desktop entry
│   ├── install.sh               portable installer for the app-image
│   └── uninstall.sh             matching uninstaller
└── docs/                        this documentation and the screenshots
```

---

## 6. How the packaging works

Salah Guardian is deliberately built as a **non-modular** application: JavaFX sits on
the ordinary classpath rather than the module path.

That choice is driven by one detail of the JavaFX launcher. If the class named in
`Main-Class` extends `javafx.application.Application`, the JVM insists that JavaFX be
present as named modules and otherwise fails with *"JavaFX runtime components are
missing"*. `Launcher` therefore does **not** extend `Application`; it simply calls
`Application.launch(SalahGuardianApp.class, args)`. With that indirection, the JavaFX
jars can be plain classpath entries — which in turn lets `jpackage` copy them like any
other dependency, with no module path to configure and nothing to go wrong on a user's
machine.

The pipeline is:

```
compileJava ─▶ jar ─▶ installDist ─▶ jpackage ─▶ .deb / .rpm / app-image
                     (flat lib/ dir)     │
                                         └─▶ jlink builds a trimmed JRE
```

`jpackage` invokes `jlink` with an explicit, minimal module set:

| Module | Why |
|---|---|
| `java.base` | the platform |
| `java.desktop` | AWT (system tray) and JavaFX's rendering stack |
| `java.logging` | SLF4J/Logback plumbing |
| `java.naming` | required by Logback |
| `java.net.http` | the IP geolocation lookup |
| `java.prefs` | JavaFX internals |
| `java.xml` | parses `logback.xml` |
| `jdk.unsupported` | JavaFX's use of `sun.misc.Unsafe` |
| `jdk.crypto.ec` | HTTPS to the geolocation endpoint |
| `jdk.localedata` | non-English locales |

The result is roughly **130 MB** installed — the price of bundling a runtime, and the
reason the user needs no Java at all.

> The jpackage tasks resolve their binary from the Gradle **compiler** toolchain rather
> than from the JVM running Gradle. That is deliberate: `javaToolchains.launcherFor(...)`
> will happily return a headless JRE that has no `jpackage` and an incomplete module set,
> whereas `compilerFor(...)` only ever resolves a full JDK.

---

## 7. Testing

```bash
./gradlew test                                   # everything
./gradlew test --tests '*AdhanPrayerTime*'       # one class
./gradlew test --tests '*.copesWithHighLatitude' # one method
./gradlew jacocoTestReport                       # coverage
```

The suite covers the calculation engine, the schedule cache, the reminder planner,
configuration persistence and recovery, the location provider chain, notification
composition and fallback, autostart entry generation, and the time utilities.

It runs **headless** — `java.awt.headless=true` is set for the test task — and touches
neither the network nor D-Bus. The location and notification chains are exercised
through stubs, and the IP payload parser through recorded responses.

Two behaviours worth knowing about, because they are pinned by tests:

- **adhan-java does not clear the millisecond field**, so the current wall clock would
  otherwise leak into every prayer time. `AdhanPrayerTimeCalculator` truncates to whole
  minutes, and `returnsWholeMinutes` asserts that two calculations of the same day are
  identical.
- **Inside the polar circles adhan returns no times at all** on days with no
  astronomical twilight. The calculator falls back to the nearest usable latitude (45°)
  and flags the result; `copesWithHighLatitude` covers both the fallback and the normal
  path at the same location.

---

## 8. Build troubleshooting

**`Cannot find a Java installation ... matching {languageVersion=21}`**
No JDK 21 was found. Install one (section 1), or pass its path:
`./gradlew build -Porg.gradle.java.installations.paths=/path/to/jdk-21`.

**`jpackage was not found at ...`**
You have a JRE, not a JDK. Install `openjdk-21-jdk` / `java-21-openjdk-devel`.

**`jlink failed with: Error: Module java.sql not found`**
Gradle selected a trimmed headless JRE. Make a full JDK 21 visible to the build, or
point at it with `-Porg.gradle.java.installations.paths`.

**`Error: Application destination directory ... already exists`**
Should not happen — the tasks clear their output first — but `./gradlew clean` fixes it.

**`Cannot run program "fakeroot"` / `"rpmbuild"`**
Install the distribution packaging tools listed in section 1.

**Gradle cannot download anything**
The first build needs the internet. Behind a proxy, add to `gradle.properties`:

```properties
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8080
```
