#!/usr/bin/env node
/* Checks the JavaScript that actually ships inside the app.
 *
 * terminal.html is the pane. Its logic cannot be reached from a JVM unit test,
 * and a Kotlin copy of the same walk would only ever prove the copy, so the
 * function is pulled out of the asset itself and run against a fake xterm
 * buffer. Same idea as the panel's tools/frontend_check.js.
 *
 *   node tools/terminal_check.js
 */
"use strict";

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const HTML = path.join(ROOT, "app/src/main/assets/terminal.html");

let failed = 0;
function check(name, ok, got) {
  if (ok) { console.log("  ok   " + name); return; }
  failed++;
  console.log("  FAIL " + name + (got === undefined ? "" : "  got: " + JSON.stringify(got)));
}

/* Pull one assignment out of the page rather than running the whole script:
 * the rest of it builds a real Terminal, which does not exist out here. */
function extract(source, name) {
  const start = source.indexOf("window." + name + " = ");
  if (start < 0) throw new Error("no " + name + " in terminal.html");
  const open = source.indexOf("{", start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "{") depth++;
    else if (source[i] === "}") {
      depth--;
      if (depth === 0) return source.slice(start, i + 1) + ";";
    }
  }
  throw new Error("unbalanced braces in " + name);
}

const html = fs.readFileSync(HTML, "utf8");

/* A buffer whose row y holds "line <y>", with a viewport parked anywhere. */
function fakeTerm(rows, baseY, cursorY) {
  return {
    buffer: {
      active: {
        baseY: baseY,
        cursorY: cursorY,
        getLine: (y) => (y >= 0 && y < rows.length
          ? { translateToString: () => rows[y] }
          : undefined),
      },
    },
  };
}

function runTermText(term, n) {
  let out = null;
  // On a device CliqueBridge is a bare global that addJavascriptInterface
  // injects, which is why the page guards on window.CliqueBridge and then
  // calls it unqualified. The harness has to supply it both ways.
  const bridge = { onText: (t) => { out = t; } };
  const win = { CliqueBridge: bridge };
  const body = extract(html, "termText");
  new Function("term", "window", "CliqueBridge",
    body + "\nwindow.termText(" + n + ");")(term, win, bridge);
  return out;
}

console.log("the pane's text, for something that can select it");
{
  const rows = ["one", "two", "three", "four", "five"];
  const term = fakeTerm(rows, 0, 4);

  check("hands the text to the bridge rather than returning it",
        runTermText(term, 10) === "one\ntwo\nthree\nfour\nfive");
  check("the last n lines, not the first",
        runTermText(term, 2) === "four\nfive");
  check("stops at the cursor, ignoring anything below it",
        runTermText(fakeTerm(rows, 0, 2), 10) === "one\ntwo\nthree");
  check("counts scrollback, so baseY is included",
        runTermText(fakeTerm(rows, 3, 1), 2) === "four\nfive");
  check("asking for nothing gives nothing", runTermText(term, 0) === "");
  check("a negative count gives nothing", runTermText(term, -5) === "");
  check("trailing blank rows are dropped",
        runTermText(fakeTerm(["one", "", "  "], 0, 2), 10) === "one");
  check("a blank row in the middle survives",
        runTermText(fakeTerm(["one", "", "two"], 0, 2), 10) === "one\n\ntwo");
  check("a missing row is a blank line, not a crash",
        runTermText(fakeTerm(["one"], 0, 2), 10) === "one");
  check("a pane with nothing in it answers with nothing, and still answers",
        runTermText(fakeTerm([], 0, 0), 10) === "");
  check("a broken buffer answers rather than throwing",
        runTermText({ buffer: null }, 10) === "");
}

/* The pane must not move because somebody asked to read it. */
console.log("reading the pane does not disturb it");
{
  const body = extract(html, "termText");
  for (const forbidden of ["scrollToBottom", "scrollLines", "focus(", "select(", "term.write"]) {
    check("never calls " + forbidden, body.indexOf(forbidden) < 0);
  }
}

console.log("");
console.log(failed ? `FAILED ${failed}` : "all passed");
process.exit(failed ? 1 : 0);
