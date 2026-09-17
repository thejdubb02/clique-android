# CLIque for Android

A native Android client for [CLIque](https://github.com/thejdubb02/clique), the self-hosted panel that runs folder-organised coding sessions in tmux.

The prompt is a real Android `EditText`. That is the point of the app. The panel's website types into a field inside a WebView, and Android keyboards fight that field. This client does not wrap the website. Native chrome, native input, and the panel's xterm.js pane for the terminal.

MIT, matching the server. Built to go on F-Droid: no Play Services, no Firebase, no analytics.

## Pairing

On the laptop, ask the panel for a pairing code (`POST /api/pair`, or the button in the web UI). On the phone: add the panel URL, tap Pair, type the code. Case and the dash are ignored. The token is stored in `EncryptedSharedPreferences`, one per server.

## Screens

1. **Servers** — add, edit, remove. Reachability from `GET /healthz`.
2. **Sessions** — grouped by folder, alive and working shown. Pull to refresh; polls every 3s while visible.
3. **Session** — terminal in a WebView, native prompt bar pinned above the keyboard with WindowInsets. Send button, and Enter sends (Shift+Enter for a newline).
4. **Notification** when a session that was working stops, from `GET /api/sessions/<id>/wait`.

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
| xterm.js (vendored, MIT) | The panel's terminal renderer, copied from CLIque. Display only; stdin is disabled. |

## Known blocker for F-Droid: the bundled xterm.js

`app/src/main/assets/vendor/xterm.js` is the minified distribution, copied from
the server's own vendor directory. **F-Droid will not accept it in that form.**
Their inclusion policy requires that everything shipped is built from source on
their infrastructure, and a 488 KB single-line JavaScript file is a compiled
artifact by their reckoning, not source. It is MIT and its provenance is
documented, which is grounds for an appeal, not for assuming it will pass.

The fix is to build xterm.js from source during the build rather than commit the
result: F-Droid's build recipes may `sudo apt-get install npm` and run a
`prebuild` step, which is how other apps with JavaScript assets handle this. It
has to be done before submission, not after a rejection.

Sideloading the APK is unaffected. This only gates distribution through F-Droid.

## What this pass does not do

It pairs, lists sessions, opens a terminal, and sends a prompt. It does not reimplement the rest of the web panel (files, artifacts, broadcast, notes, settings). Those stay on the laptop.

## License

MIT. See `LICENSE`. xterm.js is MIT; see `app/src/main/assets/vendor/xterm.LICENSE`.
