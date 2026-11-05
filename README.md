# SuperDialer

An all-in-one Android phone app that you can set as your default phone app. Besides calling it includes a built-in web browser and built-in games.
It is not on the Play Store: download the APK from this repository's **Releases** and install it yourself. (Target device: Galaxy S23 / Android 10 or newer.)
The app is available in **English and Korean** (it follows the phone's language, or choose one in Settings > Display > Language).

## Features

- **Calls**: keypad (Korean number formatting, autocomplete), incoming / outgoing / in-call screens (mute, speaker, keypad, hold, call waiting, merge), decline with a message, missed-call notifications, answering on the lock screen, call notes, speed-dial digits
- **Video calls**: start them from the keypad or a contact, answer incoming ones with video or voice, and accept or refuse a switch to video during a call (carrier video calling through Android's Telecom `VideoCall` API; needs a line that supports it)
- **Recents & contacts**: search (name, Korean initials, number), filters, grouping of repeated numbers, select / delete all, favorites, contact groups, duplicate clean-up and merging, blocked numbers, call statistics, text (SMS/MMS) history next to the calls, text conversations, sending photos through the messages app
- **KakaoTalk helpers**: chat / VoiceTalk / FaceTalk buttons on a contact, and an opt-in inbox of KakaoTalk notifications with notification replies (KakaoTalk has no public API, so only what Android allows)
- **Browser**: speed-dial start page with folders and drag-to-reorder, full-screen pages, tab grid with page previews, **private tabs** (separate WebView profile, no history), bookmarks, history, downloads and uploads, find in page, desktop site, search engine choice (Google / Naver / Microsoft), dark mode for web pages, per-site camera / microphone / location permissions, full-screen video
- **Games**: 10 games — 2048, Snake, Breakout, Crossy Chicks, Flap, Blocks, Whack-a-Mole, Stack, Dodge, Memory Pads (best scores, saved games, optional online leaderboard)
- **Account (optional)**: email or Google sign-in syncs start-page links, bookmarks, game scores and settings; everything works without it
- **Settings**: default phone app, blocked numbers, decline messages, keypad sound, themes and accent colors, text size, language, permissions, in-app updates, patch notes, error log

Technical structure and development notes are in [CLAUDE.md](CLAUDE.md) (written in Korean).
The privacy policy is at <https://jangwoo0827.github.io/phonecall/privacy.html>.

## Installing

1. In your phone's browser open this repository's [Releases](../../releases) page and download the latest `SuperDialer-vX.Y.Z.apk`.
2. **Allow installing unknown apps**: when you open the file, turn on "Allow from this source". (You can also allow your browser / My Files under Settings > Apps > Special access > Install unknown apps.)
3. **Play Protect warning**: if "Blocked as harmful" / "Unverified app" appears, tap **More details > Install anyway**. It appears because the app comes from outside Google Play.
4. **Samsung Auto Blocker**: if the installation is blocked, turn off "Block unauthorized apps" under Settings > Security and privacy > **Auto Blocker**, install, and turn it back on if you like.
5. When you open the app you are asked for the **notification permission**, then shown a screen about setting it as the **default phone app**, then a short screen explaining the permissions it uses.

### Setting it as the default phone app

- On the first-run screen choose "Set as default phone app", then pick SuperDialer in the system dialog.
- Later you can ask again under the app's **Settings tab > Default phone app**, or pick it in the phone's Settings > Apps > Default apps > Phone app.
- The in-call screens and number blocking only work while it is the default phone app.
- **Undo**: in the phone's Settings > Apps > Default apps > Phone app choose your previous phone app (Samsung Phone etc.). If calls don't work while you test, switch back at once.

### Good to know after installing

- The **lock screen phone shortcut** is separate from the default phone app. Change the bottom-left shortcut to SuperDialer in the lock screen editor (Settings > Wallpaper and style > Edit lock screen).
- **Full-screen notifications** (showing the incoming-call screen on the lock screen) may need to be allowed under "Full-screen notifications" in the app's Settings tab on Android 14 and newer.
- If the call log or text permissions can't be allowed, first try Settings > Apps > SuperDialer > top-right ⋮ > **Allow restricted settings** (an Android protection for apps installed from a file).
- Call recording: Android doesn't let ordinary apps record the call audio. The recording button uses the microphone, so it captures your voice and, with the speaker on, the other person's.
- Video calls only work on a line / carrier that supports video calling, and the phone must allow a third-party default phone app to use it. The emulator has no video calling.

## Development

You need JDK 17 and the Android SDK (platform 36). Set `sdk.dir` in `local.properties` or set `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

UI tests run on an emulator (`ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`) and always in Korean, whatever the emulator's language.

### Translations

All texts live in resource files: `app/src/main/res/values/` is English (the default) and `values-ko/` is Korean. Code never contains user-visible text directly: it calls `str(R.string.key)`.
To add a text, add the key to both `strings_manual.xml` files (or to `strings.xml`); a unit test checks that both languages have the same keys and the same `%1$s` placeholders and that the English file contains no Korean.

## Releases (GitHub Actions)

Pushing a tag that starts with `v` runs the [workflow](.github/workflows/release.yml): the emulator UI tests ([ui-tests.yml](.github/workflows/ui-tests.yml)) first, then, only if they pass, a signed release APK is built and uploaded to GitHub Releases.

### 1. Create a signing key (keystore), once

```bash
keytool -genkeypair -v -keystore superdialer-release.jks -alias superdialer \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for passwords, your name and so on. Remember the **keystore password**, the **key alias** and the **key password** (the alias in this example is `superdialer`).

> **Important**: **back up `superdialer-release.jks` somewhere safe**. If you lose it you can't ship updates with the same signature and everyone has to uninstall and reinstall. Never commit it to the repository (`.gitignore` blocks `*.jks`).

### 2. Turn the keystore into a base64 string

GitHub Secrets can't hold a file, so put it in as text (base64).

- Windows PowerShell (copies to the clipboard):
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("superdialer-release.jks")) | Set-Clipboard
  ```
- Git Bash / Linux:
  ```bash
  base64 -w0 superdialer-release.jks
  ```
- macOS:
  ```bash
  base64 -i superdialer-release.jks | pbcopy
  ```

### 3. Add the GitHub Secrets

Under the repository's **Settings > Secrets and variables > Actions > New repository secret** create four secrets:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 string from step 2 |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias (e.g. `superdialer`) |
| `KEY_PASSWORD` | the key password |

### 4. Release by pushing a tag

```bash
git tag v0.2.0
git push origin v0.2.0
```

You can follow it on the Actions tab; when it ends `SuperDialer-v0.2.0.apk` (and an R8 mapping file) appear under Releases.
The version name comes from the tag (`v0.2.0` becomes `0.2.0`) and the version code from the workflow run number, so a new release updates over the previous install.

### Good to know

- The release APK has a **different signature than a debug build**, so they can't be installed over each other. If a debug build installed with `adb install` is on the phone, remove it (deleting its data) before installing a release. Once a release key is installed, later releases install as updates.
- Without the Secrets the workflow stops at once with "KEYSTORE_BASE64 secret is not set".
- Running `./gradlew assembleRelease` locally without a key produces an unsigned `app-release-unsigned.apk`. To sign locally set the environment variables `KEYSTORE_FILE` (file path), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
- The release build is shrunk with R8 (about 3.5 MB). R8 bugs only show up in release builds, so after big changes install a release build and look around.

### When Actions fails

1. Repository **Actions** tab > the failed run > open the `release` job on the left to see the steps. Expand the step marked with a red ✗ to find the cause.
2. Messages from the `Decode and check signing keystore` step and what they mean:
   - `Secret ... is empty or not set`: the Secret is missing or misspelled. Create it with the exact name under **Secrets**, not **Variables**.
   - `KEYSTORE_BASE64 is not valid base64`: the value is broken or only partly pasted. Run the PowerShell command from step 2 again and paste the whole thing.
   - `keystore password was incorrect` / alias error: `KEYSTORE_PASSWORD` or `KEY_ALIAS` differs from what you chose when creating the keystore.
3. After fixing a Secret you can press **"Re-run all jobs"** on the same run. If you changed the workflow file (`release.yml`) the tag still points at the old version, so push a **new tag** (e.g. `v0.2.1`) for the new workflow to apply.
4. A failing UI test stops the release before the APK is built, so nothing reaches the phone. The `Name the failed tests` step of ui-tests.yml lists the failed tests as annotations on the run page; fix the cause and push a new tag.
