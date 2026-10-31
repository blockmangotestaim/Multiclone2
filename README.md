# MultiClone

Android app cloner (multiple accounts / parallel apps) built on the open-source
**BlackBox** virtual-app engine ([ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox) by default).
Clone installed apps or import APKs, run up to 10 isolated instances per app,
pin launcher shortcuts, and manage everything from one list. No root needed.

## Build in the cloud (no Android Studio, no signing keys)
1. Create a new GitHub repo and upload everything in this folder.
2. **Actions → Build Debug APK → Run workflow.**
   Inputs: JDK (17/21), engine repo/ref, engine module name (default `Bcore`), NDK version, Gradle version, targetSdk.
3. When it finishes, download **MultiClone-debug-apk** from the run's *Artifacts*. The APK is signed with the
   auto-generated debug key, so it installs directly.

### How the engine is wired in
`scripts/link-engine.sh` clones the engine repo, then generates `engine-modules.gradle` so its library
modules are compiled as part of this build (this keeps the engine's transitive dependencies).
Alternative: drop a prebuilt engine `.aar` into `app/libs/` and build normally.
Only `CloneEngine.java` talks to the engine, so engine changes/swaps stay in one file.

## Features
| Need | How |
|---|---|
| Clone / launch / manage | List UI; tap = launch, long-press = launch / clone again / shortcut / remove |
| Separate data | Each instance = its own virtual user id (0–9) → isolated data dir |
| Permissions | Host requests storage/notifications; clones request their own at runtime; shortcut to host settings in menu |
| Launcher shortcuts | Pinned shortcut → `ShortcutActivity` → starts that instance |
| 32-bit + 64-bit | `abiFilters armeabi-v7a, arm64-v8a`; engine runs guest in matching ABI |

## Honest limitations
- **I could not compile this here.** The engine API calls (`installPackageAsUser`, `launchApk`,
  `getInstalledPackages`, `uninstallPackageAsUser`) follow the engine README, but engine forks differ.
  If the first Actions run fails, read the log: most fixes are in `CloneEngine.java` or the `engine_module` input.
- **GameGuardian:** works only if GG itself runs *inside* the same virtual space as the target game; compatibility
  depends on the engine and the Android version, and is not guaranteed. Use on offline games only; online games
  with anti-cheat will detect virtual spaces and may ban accounts.
- 32-bit apps require hardware that still supports 32-bit execution. Split APKs / .apks / .xapk are not imported
  from the file picker (installed apps are fine).
- Android 14/15+ tightens background and storage rules; apps like banking, Play-Integrity-protected apps,
  or DRM apps often refuse to run in virtual spaces.
- targetSdk defaults to 28 for engine compatibility, so this is for sideloading, not Google Play.

## License
Host app code: MIT. The engine and its dependencies keep their own licenses.
