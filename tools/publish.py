#!/usr/bin/env python3
"""Publish a signed CLIque Android release to our F-Droid repo.

    python3 tools/publish.py            # build, sign-check, index, rsync to vps1
    python3 tools/publish.py --dry-run  # build and sign-check only

Stops at the first failure with a message that says what to do.
Never prints the keystore password or signing.properties.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle.kts"
APK_BUILT = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
ICON_SRC = ROOT / "app" / "src" / "main" / "res" / "mipmap-xxhdpi" / "ic_launcher.png"

FDROID = Path("/root/.clique-android/fdroid")
INDEX = FDROID / "repo" / "index-v2.json"
ICON_DST = FDROID / "repo" / "icons" / "icon.png"
FDROID_BIN = Path("/root/.cache/clique-fdroid/bin/fdroid")

ANDROID_HOME = "/opt/android-sdk"
BUILD_TOOLS = "/opt/android-sdk/build-tools/35.0.0"
APKSIGNER = Path(BUILD_TOOLS) / "apksigner"
JAVA_HOME = "/usr/lib/jvm/java-21-openjdk-amd64"

PACKAGE = "dev.useclique.android"
RELEASE_CERT_SHA256 = "34f4541ff65fc23decec35acda15530c799c5d94b45248544cbf1177849b028f"
LIVE_INDEX = "https://fdroid.useclique.dev/repo/index-v2.json"
REPO_URL = "https://fdroid.useclique.dev/repo"
REMOTE = "vps1:/var/www/fdroid"

VERSION_CODE_RE = re.compile(r"^\s*versionCode\s*=\s*(\d+)\s*$", re.MULTILINE)
VERSION_NAME_RE = re.compile(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', re.MULTILINE)
CERT_RE = re.compile(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})")


def die(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def tool_env() -> dict[str, str]:
    env = os.environ.copy()
    env["ANDROID_HOME"] = ANDROID_HOME
    env.setdefault("JAVA_HOME", JAVA_HOME)
    env["PATH"] = BUILD_TOOLS + os.pathsep + env.get("PATH", "")
    return env


def run(argv: list[str], cwd: Path, *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        argv,
        cwd=cwd,
        env=tool_env(),
        text=True,
        capture_output=capture,
        check=False,
    )


def parse_version_code(gradle_text: str) -> int:
    found = VERSION_CODE_RE.search(gradle_text)
    if not found:
        die(
            "Could not read versionCode from app/build.gradle.kts. "
            "It should look like: versionCode = 6"
        )
    return int(found.group(1))


def parse_version_name(gradle_text: str) -> str:
    found = VERSION_NAME_RE.search(gradle_text)
    if not found:
        die(
            "Could not read versionName from app/build.gradle.kts. "
            "It should look like: versionName = \"0.1.5\""
        )
    return found.group(1)


def load_index(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        die(
            f"{path} is missing. The local F-Droid tree at {FDROID} is incomplete."
        )
    except json.JSONDecodeError as exc:
        die(f"{path} is not valid JSON: {exc}")
    raise AssertionError("unreachable")


def highest_version_code(index: dict, package: str = PACKAGE) -> int:
    versions = index.get("packages", {}).get(package, {}).get("versions", {})
    codes: list[int] = []
    for entry in versions.values():
        if not isinstance(entry, dict):
            continue
        manifest = entry.get("manifest") or {}
        code = manifest.get("versionCode", entry.get("versionCode"))
        if code is not None:
            codes.append(int(code))
    return max(codes) if codes else 0


def require_newer_version(local: int, highest: int) -> None:
    if local > highest:
        return
    die(
        f"versionCode {local} is not greater than {highest} already in the "
        f"F-Droid repo. Bump versionCode in app/build.gradle.kts before "
        f"publishing. Android will not offer an update for an equal or lower "
        f"code, and it fails silently."
    )


def working_tree_status(repo: Path) -> str:
    result = run(["git", "status", "--porcelain"], cwd=repo, capture=True)
    if result.returncode != 0:
        err = (result.stderr or result.stdout or "").strip()
        die(
            "git status failed. Run this from a git checkout of clique-android"
            + (f": {err}" if err else ".")
        )
    return result.stdout.strip()


def require_clean_tree(status: str) -> None:
    if status:
        die(
            "Working tree is dirty. Commit or stash so the published APK "
            "traces back to a git commit.\n"
            + status
        )


def cert_sha256(apksigner_output: str) -> str | None:
    found = CERT_RE.search(apksigner_output)
    return found.group(1).lower() if found else None


def require_release_cert(sha: str | None) -> None:
    if sha == RELEASE_CERT_SHA256:
        return
    got = sha or "(none)"
    die(
        f"APK certificate SHA-256 is {got}, not the release key "
        f"{RELEASE_CERT_SHA256}. This APK was signed with the debug key or "
        f"another keystore. A debug APK that reaches the repo cannot be "
        f"replaced later: every phone would have to uninstall first. Check "
        f"that /root/.clique-android/signing.properties exists and that "
        f"assembleRelease used it."
    )


def fetch_index(url: str) -> dict:
    request = urllib.request.Request(
        url, headers={"User-Agent": "clique-android-publish"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, ValueError, json.JSONDecodeError) as exc:
        die(
            f"Could not fetch {url}: {exc}. "
            f"Check that {REPO_URL} is up and serving index-v2.json."
        )
    raise AssertionError("unreachable")


def verify_apk_signature(apk: Path) -> None:
    if not APKSIGNER.exists():
        die(
            f"apksigner not found at {APKSIGNER}. "
            f"Set ANDROID_HOME={ANDROID_HOME} and put {BUILD_TOOLS} on PATH."
        )
    result = run(
        [str(APKSIGNER), "verify", "--print-certs", str(apk)],
        cwd=ROOT,
        capture=True,
    )
    output = (result.stdout or "") + (result.stderr or "")
    if result.returncode != 0:
        die(
            "apksigner verify failed. The APK is unsigned or malformed.\n"
            + output.strip()
        )
    require_release_cert(cert_sha256(output))


def fdroid_update() -> None:
    if not FDROID_BIN.exists():
        die(
            f"{FDROID_BIN} not found. Use the venv at /root/.cache/clique-fdroid, "
            f"not /usr/bin/fdroid (2.2.1 cannot parse a modern APK)."
        )
    result = run([str(FDROID_BIN), "update"], cwd=FDROID)
    if result.returncode != 0:
        die(
            "fdroid update failed. Use /root/.cache/clique-fdroid/bin/fdroid "
            "with ANDROID_HOME=/opt/android-sdk and "
            f"{BUILD_TOOLS} on PATH, not /usr/bin/fdroid."
        )


def publish_repo() -> None:
    for name in ("repo", "archive"):
        src = FDROID / name
        if not src.is_dir():
            die(f"{src} is missing. The local F-Droid tree is incomplete.")
        dest = f"{REMOTE}/{name}/"
        result = run(
            ["rsync", "-a", "--delete", str(src) + "/", dest],
            cwd=FDROID,
        )
        if result.returncode != 0:
            die(
                f"rsync of {name}/ to {dest} failed. "
                f"Check SSH to vps1 and that /var/www/fdroid exists."
            )
    result = run(
        ["ssh", "vps1", "chown -R www-data:www-data /var/www/fdroid"],
        cwd=ROOT,
    )
    if result.returncode != 0:
        die("chown of /var/www/fdroid on vps1 failed. SSH in and fix ownership.")


def confirm_live(version_code: int) -> None:
    live = highest_version_code(fetch_index(LIVE_INDEX))
    if live < version_code:
        die(
            f"{LIVE_INDEX} does not serve versionCode {version_code} "
            f"(highest there is {live}). The rsync may have failed or the "
            f"web server is serving a stale copy. Check vps1:{REMOTE}/repo/."
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="build, test and check the signature; do not copy, index or rsync",
    )
    args = parser.parse_args(argv)

    gradle_text = GRADLE.read_text(encoding="utf-8")
    version_code = parse_version_code(gradle_text)
    version_name = parse_version_name(gradle_text)
    highest = highest_version_code(load_index(INDEX))
    dirty = working_tree_status(ROOT)

    # --dry-run still builds and checks the signature, but does not refuse a
    # dirty tree or an already-published versionCode: those gates only matter
    # when we would copy the APK into the repo.
    if args.dry_run:
        if dirty:
            print(
                "dry-run: working tree is dirty (a real publish would refuse)",
                flush=True,
            )
        if version_code <= highest:
            print(
                f"dry-run: versionCode {version_code} is not greater than "
                f"{highest} in the repo (a real publish would refuse)",
                flush=True,
            )
        else:
            print(f"versionCode {version_code} > repo {highest}", flush=True)
    else:
        require_clean_tree(dirty)
        require_newer_version(version_code, highest)

    print("gradle testDebugUnitTest assembleRelease", flush=True)
    gradle = run(
        [str(ROOT / "gradlew"), "testDebugUnitTest", "assembleRelease"],
        cwd=ROOT,
    )
    if gradle.returncode != 0:
        die(
            "gradle testDebugUnitTest assembleRelease failed. "
            "Fix the tests or the release build before publishing."
        )

    if not APK_BUILT.is_file():
        die(f"Release APK missing at {APK_BUILT} after a successful gradle run.")

    verify_apk_signature(APK_BUILT)
    print(f"signed with release key ({RELEASE_CERT_SHA256[:12]}...)", flush=True)

    dest_apk = FDROID / "repo" / f"{PACKAGE}_{version_code}.apk"
    if not args.dry_run:
        shutil.copy2(APK_BUILT, dest_apk)
        print(f"copied {dest_apk.name}", flush=True)
        fdroid_update()
        if not ICON_SRC.is_file():
            die(
                f"{ICON_SRC} is missing. The launcher icon has to exist so "
                f"fdroid update does not generate a placeholder."
            )
        ICON_DST.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ICON_SRC, ICON_DST)
        fdroid_update()
        publish_repo()
    else:
        print(f"dry-run: would copy to {dest_apk}", flush=True)

    confirm_live(version_code)
    label = "dry-run" if args.dry_run else "published"
    print(f"{label} {version_name} (versionCode {version_code})", flush=True)
    print(REPO_URL, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
