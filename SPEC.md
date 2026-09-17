# clique-android — what to build

A native Android client for CLIque, distributed through F-Droid.

CLIque is a self-hosted panel that runs folder-organised, CLI-agnostic coding
sessions in tmux and serves a web UI. It is Python standard library only, and
its HTTP API is the whole surface: anything the web panel does, a client can
do. Source: https://github.com/thejdubb02/clique · MIT.

## Why this app exists, which decides what it is

Every input bug this product has had was an Android keyboard fighting a text
field inside a web view. The most recent took three attempts to fix: accepting
a Gboard spelling suggestion desynchronised the keyboard from the field, and
from then on every keystroke replayed text that had already been sent, until
the app was restarted.

**So the prompt input must be a real native EditText. That is the point of the
app.** Not a wrapper around the website. If this ships as a WebView over the
whole panel it has failed, because it will inherit the exact bug it exists to
remove.

The terminal itself stays a WebView running the panel's xterm.js pane. That is
deliberate and is not a contradiction: reimplementing a terminal emulator is
the trap this project names explicitly ("a driver, not an IDE"), and xterm.js
already does that job well. Native chrome, native input, web-rendered terminal.

## Hard constraints

- **F-Droid inclusion.** Free software, builds reproducibly from source on
  their infrastructure. No Google Play Services, no Firebase, no proprietary
  dependency, no analytics, no crash reporters. Gradle wrapper committed.
- **MIT**, matching the server.
- Kotlin. minSdk 26, targetSdk 35, compileSdk 35. AndroidX only.
- Dependencies are a liability, not a convenience. Justify each one. Prefer
  the platform: `HttpURLConnection` or OkHttp only if it earns its place, no
  Retrofit for a dozen endpoints, no dependency injection framework, no
  image loader.
- Must work against a self-signed or private-CA server, and over a VPN, since
  that is how these are usually reached. Never disable certificate validation
  to achieve that.

## Getting in: pairing

Typing a forty-character token into a phone is why this app did not exist. The
server has a pairing flow as of 0.67.7.

- `POST /api/pair` (authenticated, done by the user in the web panel on a
  laptop) displays a code like `K7PM-3XQF`. Two groups of four, alphabet has no
  0/O, 1/I/L or U.
- `POST /api/pair/claim` with `{"code": "k7pm-3xqf", "name": "Justin's Pixel"}`
  is the one unauthenticated write. Returns `201` and
  `{"token": "mxp_...", "id": "tk_...", "name": "..."}`.
- Case and the dash are ignored. Single use, two-minute life, five wrong
  guesses burn it, every refusal is an identical `403`.

Afterwards every call carries `Authorization: Bearer mxp_...`.

Store the token in `EncryptedSharedPreferences` (androidx.security-crypto,
which is AndroidX and F-Droid-clean). The app supports several servers, so the
store is keyed per server.

## The API it needs

- `GET /api/state` — the whole panel in one object, polled every 3s by the web
  UI: `version`, `home`, `folders`, `sessions`, `clis`, `settings`. Sessions
  carry id, name, cli, cwd, folder, alive and a working indicator.
- `POST /api/sessions` → 201, `{cli, cwd, name, folder}`.
- `POST /api/sessions/<id>/send` — `{text, enter}`. **This is what the native
  prompt posts.**
- `POST /api/sessions/<id>/kill`, `POST /api/sessions/<id>/start`,
  `DELETE /api/sessions/<id>`.
- `GET /api/sessions/<id>/wait` — long-poll until a session stops working.
  Use this for the notification, not a busy loop.
- `GET /healthz` — `{"ok": true}`, unauthenticated, for reachability.
- The terminal stream is a WebSocket at `/ws`.

## Screens

1. **Servers.** Add, edit, remove. Each has a name, a base URL, and a paired
   token. Adding one is: type the URL, tap Pair, type the code from the panel.
   Show reachability from `/healthz`.
2. **Sessions.** The list for the selected server, grouped by folder, showing
   which are alive and which are working. Pull to refresh; poll while visible.
3. **Session.** Terminal in a WebView filling the screen, with a **native
   input bar pinned above the keyboard**: multi-line EditText, a send button,
   and an Enter key that sends. Use WindowInsets for the keyboard, not a
   guessed height.
4. **Notification** when a session that was working stops, driven by
   `/api/sessions/<id>/wait`. A foreground service only if one is genuinely
   required; say so if you use one, because it is a battery and review cost.

## What to deliver in this pass

A repository that **assembles**. `./gradlew assembleDebug` must succeed with
the Android SDK at `/opt/android-sdk` (SDK 35, build-tools 35.0.0, JDK 21).

Write the whole project: settings.gradle.kts, build.gradle.kts, the wrapper,
AndroidManifest.xml, Kotlin sources, resources, `.gitignore`, a README that
says what it is and how to build it, and the F-Droid metadata skeleton under
`metadata/` in the layout fdroiddata expects.

Correctness over surface. A smaller app that builds and pairs and sends a
prompt is worth far more than a complete-looking one that does not compile.
Do not stub a screen and leave a TODO where the hard part was; if something
cannot be finished, say so plainly in the README rather than faking it.
