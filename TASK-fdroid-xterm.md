# Job: build xterm.js from source, so F-Droid will take this app

## Why
`app/src/main/assets/vendor/*.js` are minified distributions copied from the
CLIque server. F-Droid's inclusion policy treats a 488 KB single-line JS file as
a compiled artifact, not source, and will reject the submission. This has to be
fixed before we submit, not after a rejection.

## What to do

1. Stop committing the built JS. Delete `app/src/main/assets/vendor/*.js` from
   git and add that path to `.gitignore`. Keep `xterm.LICENSE`.

2. Add `tools/build-xterm.sh` that produces those files from source:
   - clone xterm.js at a **pinned tag** (match the version the server vendors,
     read it from `/root/platform/clique/clique/web/vendor/` if it is recorded
     there, otherwise pin the latest stable and write the tag down)
   - `npm ci && npm run build` (plus the addon packages: fit, unicode11, canvas)
   - copy the resulting `xterm.js`, `xterm.css`, `addon-fit.js`,
     `addon-unicode11.js`, `addon-canvas.js` into
     `app/src/main/assets/vendor/`
   - idempotent, and a no-op if the outputs already exist and match the pin

3. Wire it into gradle so a normal `./gradlew assembleDebug` still works for
   someone who has never run the script: a task that runs `tools/build-xterm.sh`
   before `mergeDebugAssets`/`mergeReleaseAssets` **only when the vendor files
   are missing**. A build with the files already present must not touch the
   network, because this box builds with `--offline`.

4. Update `metadata/dev.useclique.android.yml` with the F-Droid build recipe:
   `sudo apt-get install -y npm` under `sudo:` and the script under `prebuild:`.

5. Update the README: replace the "Known blocker for F-Droid" section with how
   it is actually built now.

## How to verify, and you must
- `rm -rf app/src/main/assets/vendor/*.js && ./gradlew assembleDebug` succeeds
  and the APK contains the vendor JS. Check with
  `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep vendor`.
- A second `./gradlew --offline assembleDebug` succeeds with no network.
- Do not change any Kotlin, any layout, or anything about how the app behaves.
  This is a build-system change only.

## Rules
- No secrets in any file. No new runtime dependencies.
- Do not commit. Leave the work in the tree and report what you changed.
