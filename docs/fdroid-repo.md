# The CLIque F-Droid repository

`https://fdroid.useclique.dev` — our own F-Droid repository, so a release
reaches a phone as an ordinary update notification and nobody downloads an APK
by hand. Built 2026-09-17.

This is **not** the official F-Droid catalogue. That is a separate thing, takes
weeks of review, and matters for strangers finding CLIque rather than for us.
Both can exist; see `metadata/dev.useclique.android.yml` for the recipe the
official one would use.

## Where each piece lives, and why

| | |
|---|---|
| Signing keystore | `/root/.clique-android/release.keystore` on the **devbox**, 0600 |
| Its password | `/root/.clique-android/signing.properties`, 0600, read by Gradle |
| Backup | Vaultwarden, item *CLIque Android release keystore (PERMANENT - do not regenerate)*, with the keystore file attached |
| Repo tree | `/root/.clique-android/fdroid` on the devbox |
| `fdroid` binary | `/root/.cache/clique-fdroid/bin/fdroid` |
| Served from | `vps1:/var/www/fdroid`, nginx vhost `fdroid.useclique.dev` |
| DNS | Cloudflare, `fdroid.useclique.dev` A → 187.77.19.181, dns-only |
| TLS | Let's Encrypt via `--dns-cloudflare`, renews on vps1's existing timer |

**The index is signed on the devbox and the result rsynced.** vps1 serves static
files and nothing else. The signing key is never on a public-facing server.

## Four things that will bite

**1. The Ubuntu `fdroidserver` package cannot read a modern APK.** 2.2.1-2 ships
an androguard that dies with `ResParserError: res1 must be zero!` on anything
built by a current AAPT2. Hence the venv at `/root/.cache/clique-fdroid`, which
holds 2.4.5 with androguard 4.1.4. Use that binary, never `/usr/bin/fdroid`. It
needs `ANDROID_HOME=/opt/android-sdk` and
`/opt/android-sdk/build-tools/35.0.0` on `PATH`.

**2. The vhost must never go behind Tinyauth.** An F-Droid client is a machine
path: it cannot log in, and a 302 to a login page reads to it as a broken
repository. Same rule as a webhook or a heartbeat, and the vhost says so in a
comment at the top.

**3. The signing key is the app's identity, permanently.** Android ties an
installed app to the key that signed it. Replacing this keystore means every
existing install has to be uninstalled and reinstalled by hand, and there is no
way around that. `tools/publish.py` checks every APK's certificate SHA-256
against `34f4541ff65f...` before copying anything, because a debug-signed APK
that reaches the repo cannot be replaced by a correct one afterwards.

**4. A phone that already has a sideloaded build must uninstall first.** Those
were signed with the debug key. This is a one-time cost per device.

## Publishing

`python3 tools/publish.py`. Everything it does and refuses is in
`releasing.md`.

## Adding it to a phone

Tapping this opens F-Droid on its add-repository screen:

```
fdroidrepos://fdroid.useclique.dev/repo?fingerprint=34F4541FF65FC23DECEC35ACDA15530C799C5D94B45248544CBF1177849B028F
```

Or, by hand: F-Droid, Settings, Repositories, add
`https://fdroid.useclique.dev/repo`. **The `/repo` matters.** Without it the
client looks for an index that is not there and reports "invalid repository".

Android then asks twice, once each: allow F-Droid to install apps, and Play
Protect noting it has not seen this app before.

## Known rough edge

The repository shows a generated placeholder icon rather than the CLIque one.
`fdroid update` clears `repo/icons/` at the start of every run, and
`config/icon.png` is not picked up by 2.4.5 either. The app's own icon is
correct; this is only the repository's entry in the client.
