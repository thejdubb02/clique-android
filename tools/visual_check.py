"""Look at the pane, because nothing else here can.

tools/terminal_check.js proves the link matcher picks the right rows and
columns. It cannot prove anything reached the screen, and the bug this exists
for was exactly that: xterm paints its link underline on hover, a phone has no
hover, so every URL in the pane was tappable and completely invisible while the
whole suite stayed green.

So this opens the real asset in a real browser and asserts the underline is
there, sized, and not swallowing the tap.

**A development tool, not part of the app.** It needs Chromium and Playwright,
so it borrows the panel's virtualenv rather than adding anything to the build:

    ~/.cache/clique-visual/bin/python tools/visual_check.py

Screenshots land in /tmp/clique-android-visual/.
"""

from __future__ import annotations

import http.server
import functools
import socket
import sys
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
SHOTS = Path("/tmp/clique-android-visual")

failed = 0


def check(name: str, ok: bool, got=None) -> None:
    global failed
    if ok:
        print(f"  ok   {name}")
        return
    failed += 1
    print(f"  FAIL {name}" + ("" if got is None else f"  got: {got!r}"))


def serve() -> int:
    """The vendored xterm is loaded with relative <script> tags, which file://
    will not always give us. One throwaway server on a free port instead."""
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(ASSETS))
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    httpd = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    httpd.daemon_threads = True
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return port


def main() -> int:
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("playwright is missing: see the docstring", file=sys.stderr)
        return 2

    SHOTS.mkdir(parents=True, exist_ok=True)
    port = serve()

    with sync_playwright() as p:
        browser = p.chromium.launch()
        # A phone, because that is the only place this feature matters.
        page = browser.new_page(viewport={"width": 390, "height": 780})
        page.goto(f"http://127.0.0.1:{port}/terminal.html")
        page.wait_for_function("window.term && window.termVisibleLinks")

        print("a URL the pane printed is visible, not just tappable")
        page.evaluate(
            "term.write('see https://example.com/docs\\r\\n"
            "and fdroid.useclique.dev/repo\\r\\n')"
        )
        # The rebuild is on a 100ms trailing debounce off onRender.
        page.wait_for_timeout(600)

        deco = page.evaluate(
            """() => Array.from(
                 document.querySelectorAll('.xterm-decoration')
               ).map((el) => {
                 const r = el.getBoundingClientRect();
                 const s = getComputedStyle(el);
                 return {
                   w: Math.round(r.width), h: Math.round(r.height),
                   top: Math.round(r.top),
                   border: s.borderBottomWidth + ' ' + s.borderBottomColor,
                   pointer: s.pointerEvents,
                 };
               })"""
        )
        check("both URLs are underlined", len(deco) == 2, deco)
        check("each underline is as wide as the URL it sits under",
              all(d["w"] > 40 for d in deco), deco)
        check("each underline has a visible bottom border",
              all(d["border"].startswith("1px") and "rgba(0, 0, 0, 0)" not in d["border"]
                  for d in deco), deco)
        check("the underline does not swallow the tap",
              all(d["pointer"] == "none" for d in deco), deco)
        check("the two underlines are on different rows",
              len({d["top"] for d in deco}) == 2, deco)
        check("the underlines are on screen",
              all(0 < d["top"] < 780 for d in deco), deco)

        page.screenshot(path=str(SHOTS / "links.png"))

        print("scrolling a link out of view takes its underline with it")
        page.evaluate("term.write('\\r\\n'.repeat(60))")
        page.wait_for_timeout(600)
        gone = page.evaluate("document.querySelectorAll('.xterm-decoration').length")
        check("no underline is left behind", gone == 0, gone)
        page.screenshot(path=str(SHOTS / "scrolled.png"))

        browser.close()

    print()
    print(f"FAILED {failed}" if failed else "all passed")
    print(f"screenshots: {SHOTS}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
