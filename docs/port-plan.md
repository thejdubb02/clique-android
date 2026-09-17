# What the Android client still has to do

The web panel is the whole product. This app is a subset, and the subset was
picked to prove the architecture rather than to be usable for a day's work.
This is the list of what has to come across, in the order it has to come.

Everything here already exists server-side and is documented in
`/root/platform/clique/API.md`. Nothing on this list needs a new endpoint
unless it says so, which is the point: **the API is the whole surface**, so a
port is a UI job, not a protocol job.

Status as of 0.1.6, 2026-09-17.

The app ships from our own F-Droid repository at https://fdroid.useclique.dev,
so a release reaches a phone as an ordinary update notification. Cutting one is
`python3 tools/publish.py`; see `docs/releasing.md`.

---

## Tier 0: it is wrong today

Not missing features. Behaviour that differs from the panel in a way that reads
as a bug.

| | Endpoint | Note |
|---|---|---|
| ~~Running at the top, folders in order, Archived last~~ | `GET /api/state` | Done in 0.1.4 |
| ~~Pinned sessions float~~ | `GET /api/state` | Done in 0.1.4 |
| ~~Releasing the shared terminal size~~ | ws `release` | Done in 0.1.4 |
| Folder colour ignored | `folders[].color` | The panel colours every folder; the app draws all headers the same, so the grouping reads as arbitrary |
| No attention ring | `sessions[].signal` | The panel marks a session that is waiting or has errored. Without it the list cannot answer "which one needs me", which is the only question worth asking on a phone |
| The toolbar shows the raw session id for a moment | `GET /api/state` | Cosmetic, one line |

## Tier 1: the app cannot drive an agent without these

A phone that can read a session but not answer it is a viewer. Claude Code and
every other CLI here ask questions, and the answer is usually a keystroke.

| | Endpoint | Note |
|---|---|---|
| **Send a bare key** | `POST /api/sessions/<id>/send` `{"key":"C-c"}` | Already in the API, not exposed in the app. Escape, Ctrl-C, Enter, Up, Tab, and the digits. This is the single biggest gap: today a runaway agent cannot be stopped from the phone at all |
| **Approve / Deny** | `sessions[].signal` + `signal_note` = `"permission"`, then a key | The panel's inbox offers two buttons when a CLI is asking permission. On a phone that is the whole job |
| ~~**A key bar above the prompt**~~ | as above | Done in 0.1.5. Visible buttons at 48dp, riding the keyboard with the prompt, with spoken labels for the screen reader |
| **Scroll the terminal** | none | Verify first. Touch scrolling reaches the WebView, but xterm's viewport has never been tested under a finger on a real device |
| **Rename / move a session** | `PATCH /api/sessions/<id>` | Creating one from the phone works; correcting it does not |
| **Interrupt from the list** | `POST /api/sessions/<id>/send` | Without opening the session |

## Tier 2: what makes it worth picking up

| | Endpoint | Note |
|---|---|---|
| **Peek** | `GET /api/sessions/<id>/peek?lines=8` | The last few lines that actually said something, under each row. Built for exactly this question and currently unused by the app |
| **Prompt history** | `GET /api/prompts?limit=400` | Re-sending a prompt you already wrote beats typing it on glass |
| **Drafts** | `sessions[].draft`, `PATCH` | A half-typed prompt survives to the laptop and back. The panel already syncs these; the app throws them away |
| **Broadcast** | `POST /api/broadcast` | One instruction to a folder |
| **Transcript** | `GET /api/sessions/<id>/transcript` | Reading back what happened without attaching |
| **Notes** | `GET`/`POST /api/sessions/<id>/notes` | |
| **Diff** | `GET /api/sessions/<id>/diff` | What a session changed, from a phone, is a genuinely good review surface |
| **Resume a past conversation** | `GET /api/resumable`, then create with `cli_session_id` | |
| **Themes** | `PATCH /api/settings`, `GET /api/themes` | The app is hardcoded to one palette. The chosen theme is server state and should follow the person; note the built-in presets ship inside the web front end, so the app needs its own copy of those, and `/api/themes` returns only ones made on this panel |
| **Paste an image** | `POST /api/sessions/<id>/paste` | A screenshot from the phone into the agent's working directory. Phone-native, and the panel had to be taught it |

## Tier 3: leave it on the laptop

Listed so it is a decision rather than an oversight. These are not refused
forever, they are refused now.

File browser and editor · artifacts · uploads · storage and purge · webhook test
· model providers and keys · plan usage · working groups · adopt and orphan reaping
· manual reordering · stats and history graphs.

All of them either need a big screen, are administrative rather than
operational, or are things nobody does standing up.

## Not a feature, but on the path

- **The layout is not responsive.** Tested only at phone portrait. A tablet and
  landscape are untested.
- **One server at a time.** The app holds several but the list only ever shows
  one. The panel has no equivalent, so this is the app's own debt.
- ~~xterm.js is vendored minified, which blocks F-Droid.~~ Cleared: it builds
  from source at the 5.5.0 pin the server uses. Submitting to the **official**
  F-Droid catalogue is still outstanding and is a separate thing from our own
  repo: it takes weeks of review and matters for strangers, not for us.
- **No CI.** The repo has no remote yet, so the JVM tests added in 0.1.4 run
  only when someone runs them.
