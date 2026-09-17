# Releasing CLIque for Android

One command publishes a signed release to our F-Droid repo:

```bash
python3 tools/publish.py
```

`--dry-run` does the tests, the release build and the signature check, then stops before copying anything.

It refuses a dirty git tree, so a published APK always traces to a commit. It also refuses unless `versionCode` in `app/build.gradle.kts` is higher than anything already in the repo. Android will not offer an update for an equal or lower code, and it fails silently.

Then it runs the unit tests and a release build, checks the APK is signed with the release key, copies it into the local F-Droid tree as `dev.useclique.android_<versionCode>.apk`, rebuilds the index (and puts the real launcher icon back after `fdroid update` wipes it), and rsyncs `repo/` and `archive/` to vps1.

## Why the signature check exists

Android ties an install to the signing key. A debug-signed APK that reaches the repo cannot be replaced by a correctly signed one later: every phone has to uninstall first. The script checks the certificate SHA-256 before anything is copied.

## The keystore

The release keystore lives at `/root/.clique-android/release.keystore` and is backed up in Vaultwarden. If it is lost, every existing install has to be removed by hand before a new key can take over. Do not replace it.

## Adding the repo on a phone

In F-Droid, open Repositories and add `https://fdroid.useclique.dev/repo`.
