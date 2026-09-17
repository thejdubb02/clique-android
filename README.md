# CLIque for Android

A native Android client for [CLIque](https://github.com/thejdubb02/clique), the self-hosted panel that runs folder-organised coding sessions in tmux.

The prompt is a real Android `EditText`. That is the point of the app. The panel's website types into a field inside a WebView, and Android keyboards fight that field. This client does not wrap the website. Native chrome, native input, and the panel's xterm.js pane for the terminal.

MIT, matching the server. Built to go on F-Droid: no Play Services, no Firebase, no analytics.

## Pairing

On the laptop, ask the panel for a pairing code (`POST /api/pair`, or the button in the web UI). On the phone: add the panel URL, tap Pair, type the code. Case and the dash are ignored. The token is stored in `EncryptedSharedPreferences`, one per server.

## Installing

Add our F-Droid repository once and updates arrive on their own:

```
https://fdroid.useclique.dev/repo
```

The `/repo` matters. Detail, including the one-time uninstall if you already
sideloaded a build: `docs/fdroid-repo.md`.

## Screens

1. **Servers** — add, edit, remove. Reachability from `GET /healthz`.
2. **Sessions** — Running first, then Ungrouped, then folders, then Archived, matching the web sidebar; pinned float. Opens on running sessions only, with a toolbar toggle for all and a search over name, directory and branch. A typed search overrides the filter, because the session you are looking for is often a stopped one. Pull to refresh; polls every 3s while visible.
3. **Session** — terminal in a WebView, native prompt bar pinned above the keyboard with WindowInsets. Send button, and Enter sends (Shift+Enter for a newline). A key bar above the prompt sends one bare tmux key: Esc, ^C, Tab, up, down, Enter.
4. **Notification** when a session that was working stops, from `GET /api/sessions/<id>/wait`. When it stopped to ask permission, that notification carries Approve and Deny, which send `Enter` and `Escape` without opening the app. That indirection is the point: opening a session attaches a tmux client, the pane repaints, and the panel reads output-after-a-signal as the session having carried on, so the signal is gone in under three seconds.

## Foreground service

A foreground service (`dataSync`) runs while the app is waiting on `/wait`. That call holds a connection for up to 300 seconds, and a backgrounded app will not keep it. The service stops when nothing is being watched.

## Private CA and VPN

HTTPS still validates certificates. A private CA can be installed in Android's user CA store (this app trusts user CAs) or pasted as PEM on the server row. There is no "trust all" switch. HTTP is allowed because a panel on a Tailscale or WireGuard tailnet is often `http://`.

## Build

SDK 35, build-tools 35.0.0, JDK 21. Gradle wrapper is in the tree.

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Dependencies

Each one had to earn its place:

| Library | Why |
|---|---|
| AndroidX AppCompat, Fragment, RecyclerView, SwipeRefresh, ConstraintLayout, Core, Lifecycle, Activity | Platform UI. |
| `androidx.security:security-crypto` | Named in the spec for the token store. |
| OkHttp | REST and the `/ws` terminal stream. `HttpURLConnection` cannot do WebSockets, and one client carries the per-server CA for both. No Retrofit. |
| kotlinx-coroutines | `lifecycleScope` for UI work off the main thread. |
| xterm.js (built from source, MIT) | The panel's terminal renderer. Display only; stdin is disabled. |

## xterm.js, built from source

The JS under `app/src/main/assets/vendor/` is not committed. `./gradlew assembleDebug`
runs `tools/build-xterm.sh` when those files are missing: it clones [xterm.js](https://github.com/xtermjs/xterm.js)
at tag **5.5.0** (the same pin as the CLIque server), builds core plus the fit,
unicode11 and canvas addons, and copies the UMD bundles into `vendor/`. A later
build that already has the files does not use the network, so `--offline` still
works.

F-Droid does the same in the recipe (`sudo: apt-get install -y npm`, then the
script as `prebuild`). `xterm.LICENSE` stays in the tree.

## What this pass does not do

It pairs, lists sessions, opens a terminal, and sends a prompt. It does not reimplement the rest of the web panel (files, artifacts, broadcast, notes, settings). Those stay on the laptop.

## License

MIT. See `LICENSE`. xterm.js is MIT; see `app/src/main/assets/vendor/xterm.LICENSE`.
