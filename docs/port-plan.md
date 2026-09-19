# What the Android client still has to do

The web panel is the whole product. This app is a subset, and the subset was
picked to prove the architecture rather than to be usable for a day's work.
This is the list of what has to come across, in the order it has to come.

Everything here already exists server-side and is documented in
`/root/platform/clique/API.md`. Nothing on this list needs a new endpoint
unless it says so, which is the point: **the API is the whole surface**, so a
port is a UI job, not a protocol job.

Status as of 0.2.1, 2026-09-18.

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
| ~~Folder colour ignored~~ | `folders[].color` | Done in 0.2.0. `parseFolderColor` falls back to the muted look rather than drawing black for a value it cannot read |
| ~~No way to answer from the list~~ | `sessions[].signal` | Done in 0.2.0. The dot was already coloured by state and `saying` was already on the meta line, so what was actually missing was acting on it: a session asking permission now carries Approve and Deny on its own row. Only a genuine permission prompt gets them, not every waiting session |
| ~~The toolbar shows the raw session id for a moment~~ | `GET /api/state` | Done in 0.1.7 |

## Tier 1: the app cannot drive an agent without these

A phone that can read a session but not answer it is a viewer. Claude Code and
every other CLI here ask questions, and the answer is usually a keystroke.

| | Endpoint | Note |
|---|---|---|
| ~~**Send a bare key**~~ | `POST /api/sessions/<id>/send` `{"key":"C-c"}` | Done: the key bar in 0.1.5 (Esc, Ctrl-C, Tab, Up, Down, Enter, visible at 48dp), Approve and Deny on the notification in 0.1.7, and Interrupt on the long press in 0.2.0. A runaway agent can be stopped from the phone three different ways now. Old note follows. Already in the API, not exposed in the app. Escape, Ctrl-C, Enter, Up, Tab, and the digits. This is the single biggest gap: today a runaway agent cannot be stopped from the phone at all |
| ~~**Approve / Deny**~~ | `signal_note` = `"permission"`, then a key | Done in 0.1.7, **on the notification**, not on the session screen. Opening a session attaches a tmux client, that repaints the pane, and the panel reads output-after-a-signal as the session having carried on, so the signal is gone in under three seconds. Measured. The banner exists too but is rarely reachable |
| ~~**Select text off the pane**~~ | none | Done in 0.2.0. Not on this list originally, asked for on 2026-09-18. The last 500 rows go into a native selectable TextView, because xterm's hidden textarea stays inert and there is no touch selection in the pane by design |
| ~~**A key bar above the prompt**~~ | as above | Done in 0.1.5. Visible buttons at 48dp, riding the keyboard with the prompt, with spoken labels for the screen reader |
| ~~**Scroll the terminal**~~ | none | Done in 0.2.0, and it was broken rather than untested. xterm draws its screen over its own viewport, so a finger lands on an element whose scrollable ancestor is `body`, which is `overflow:hidden`; the desktop only works because xterm listens for wheel events, and a touch produces none. A drag is now translated into `scrollLines` |
| ~~**Rename / move a session**~~ | `PATCH /api/sessions/<id>` | Done in 0.2.0, on the long press. `"folder": null` is Ungrouped, which is why the client separates clearing it from leaving it alone |
| ~~**Interrupt from the list**~~ | `POST /api/sessions/<id>/send` | Done in 0.2.0, on the long press, no confirm: a soft interrupt is not destructive |

## Tier 2: what makes it worth picking up

| | Endpoint | Note |
|---|---|---|
| ~~**Clickable links**~~ | none | Done in 0.2.1 for URLs. The matching is lifted from the panel's `app.js` rather than written twice: `LINK_RE`, `BARE_RE`, `trimUrl` and the wrap helpers all came across, so a host with no scheme and a URL split across two rows both work, and `tools/terminal_check.js` covers them. A tap opens the phone's browser, with the scheme checked again in Kotlin because remote output reaching `ACTION_VIEW` is a way to launch another app. **Paths are still inert**: a path needs somewhere to show it and there is no file view here. **And a link is not visibly a link until it is touched**, because xterm only draws the underline on hover and a phone has none. That is its own row below |
| ~~**A link has to look like a link**~~ | none | Done in 0.3.0. xterm's own marker and decoration API draws the underline over the rows on screen, rebuilt on a shared trailing debounce off onRender and onScroll, with pointer-events none so it cannot swallow the tap. tools/visual_check.py is new and is what makes it trustworthy: it opens the real asset in headless Chromium and asserts the underline is there, sized, and on the right row. Old note follows. | xterm draws a link's underline on hover and a phone has no hover, so a URL in the pane is tappable but indistinguishable from the text around it. Nothing in the app says it is there. The repo's own rule is that nothing lives only behind hover, which makes this a defect rather than a nicety. Options, none of them settled: an absolutely positioned overlay drawn from the same cell coordinates the link provider already computes, Linkify on the Select text view, or a list of the links on screen in the overflow menu |
| ~~**Peek**~~ | `GET /api/sessions/<id>/peek?lines=8` | Done in 0.3.0. A caret on each row expands the last four lines; only an expanded row asks the server. It also found a panel bug: the frame filter counted a non-breaking space as content, so a peek came back reading a bare prompt glyph. Fixed in panel 0.67.13. Old note follows. | The last few lines that actually said something, under each row. Built for exactly this question and currently unused by the app |
| ~~**Prompt history**~~ | `GET /api/prompts?limit=400` | Done in 0.3.0. Reuse prompt in the session menu, 400 rows loaded once, filtered on the phone. Tapping one fills the prompt box and stops there. Old note follows. | Re-sending a prompt you already wrote beats typing it on glass |
| **Drafts** | `sessions[].draft`, `PATCH` | A half-typed prompt survives to the laptop and back. The panel already syncs these; the app throws them away |
| **Broadcast** | `POST /api/broadcast` | One instruction to a folder |
| **Transcript** | `GET /api/sessions/<id>/transcript` | Reading back what happened without attaching |
| **Notes** | `GET`/`POST /api/sessions/<id>/notes` | |
| **Diff** | `GET /api/sessions/<id>/diff` | What a session changed, from a phone, is a genuinely good review surface |
| **Resume a past conversation** | `GET /api/resumable`, then create with `cli_session_id` | |
| **Themes** | `PATCH /api/settings`, `GET /api/themes` | The app is hardcoded to one palette. The chosen theme is server state and should follow the person; note the built-in presets ship inside the web front end, so the app needs its own copy of those, and `/api/themes` returns only ones made on this panel |
| ~~**Paste an image**~~ | `POST /api/sessions/<id>/paste` | Done in 0.3.1. An image button in the prompt bar opens the system photo picker, which needs no permission at all, so the app still asks for nothing to read an image. The file lands in the session's own .claude-images and the path goes into the prompt; it is never sent, because the person says what they want asking about it first. Old note follows. | A screenshot from the phone into the agent's working directory. Phone-native, and the panel had to be taught it |

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
- **Search does not reach closed sessions.** The sidebar's history rows bring
  them into results; the app has no history concept.
- ~~xterm.js is vendored minified, which blocks F-Droid.~~ Cleared: it builds
  from source at the 5.5.0 pin the server uses. Submitting to the **official**
  F-Droid catalogue is still outstanding and is a separate thing from our own
  repo: it takes weeks of review and matters for strangers, not for us.
- **No CI.** The repo has no remote yet, so the JVM tests added in 0.1.4 run
  only when someone runs them.
