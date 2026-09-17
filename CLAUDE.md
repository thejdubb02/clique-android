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
- **Verify by building.** `./gradlew assembleDebug` with `ANDROID_HOME=/opt/android-sdk`.
  Never report a change as done without it.

## The one thing between here and F-Droid

The vendored xterm.js is minified, and F-Droid treats a minified bundle as a
binary rather than source. It must be built from source in the build recipe
before submission. See the README section.
