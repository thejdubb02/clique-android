# clique-android — standing rules

The Android client for CLIque, for F-Droid. Kotlin, AndroidX only. The server
lives in `../clique` and its HTTP API is the contract between them.

## Why this exists, which decides what goes in it

Every input bug CLIque has had was an Android keyboard fighting a text field
inside a web view. The last one took three attempts: accepting a Gboard
suggestion desynchronised the keyboard from the field, and every keystroke
afterwards replayed text that had already been sent until the app restarted.

**So the prompt is a real `EditText` and always will be.** An app that types
into a web view has no reason to exist, because it inherits the exact bug it
was built to remove. If a change would move typing back into the WebView, the
change is wrong.

The terminal stays xterm.js in a WebView, loaded from `file:///android_asset`,
never from the network. Reimplementing a terminal emulator is the "driver, not
an IDE" trap CLIque names explicitly. Keeping the page local also means the
JavaScript bridge can only ever be called by our own bundled page, which is why
the bridge is allowed to exist at all: two methods, both taking two integers.

## Rules

- **No Google Play Services, no Firebase, no analytics, no crash reporter.**
  F-Droid will not take it and we do not want it.
- **Never disable certificate validation.** Private CAs are supported by adding
  a PEM to the trust store alongside the platform CAs, in `api/Tls.kt`. There is
  no trust-all path and there must not be one: these servers are terminals.
- **Every dependency is justified in the README table.** Adding one means
  adding a row and defending it.
- The package id is `dev.useclique.android` and is permanent once published.
- **A green build is not a working feature.** `./gradlew assembleDebug` proves
  it compiles and nothing more. The device is at
  `agent-infra/deploy/android-emulator` (`android-emu start`, about a minute,
  reaped after three hours). Two features shipped correct and dead this way:
  a terminal that crashed on open, and an Approve button that could never
  appear. Both took minutes to find on a device and had survived reading the
  code twice.

## Signing and release

**One keystore, permanently.** Android ties an install to the key that signed
it, so replacing `/root/.clique-android/release.keystore` means every existing
install has to be removed by hand. It is backed up in Vaultwarden. A clone
without it still builds, falling back to the debug key, which is why
`tools/publish.py` checks the certificate before publishing anything.

`python3 tools/publish.py` is the only way to release. It refuses a dirty tree
and a `versionCode` that is not higher than what the repo serves, because
Android silently never offers an update for an equal or lower one.
`docs/releasing.md` and `docs/fdroid-repo.md`.

## Answering a session that is asking must not attach to it

Opening a session attaches a tmux client, the pane repaints, and the panel
reads output-after-a-signal as the session having carried on, so it clears the
signal in **under three seconds**. That is why Approve and Deny live on the
notification rather than only on the session screen. Anything else that acts on
a waiting session inherits this: do it without opening the session.

## Where the work is written down

`docs/port-plan.md` for what is left and what is deliberately refused. The
**CLIque** board in Kaneo for what is in flight. The panel's own roadmap is
`../clique/docs/next.md` and is a different list.
