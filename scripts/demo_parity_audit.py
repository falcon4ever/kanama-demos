#!/usr/bin/env python3
"""Audit Kanama demo ports for patterns that commonly drift from GDScript.

This is intentionally conservative: it fails on new risky patterns, but
allowlists places where the original demos are genuinely dynamic, such as
Godot-style damage/squash duck typing and smoke-only probes.

TEMPORARY (delete when kanama-demos#46 merges): on main this audit still reports
`tps-demo-kanama/web/kotlin-src/Part.kt:147,149`: exit_tree closes the two materials
the script duplicated and owns. That is the Kanama resource-ownership rule applied
correctly; #46 (task 97) replaces the close regexes below with that rule and clears
both findings.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


CALL_RE = re.compile(r"\.call\(\s*\"([^\"]+)\"")
SIGNAL_RE = re.compile(r"\.signal\(\s*\"([^\"]+)\"")
CONNECT_STRING_RE = re.compile(r"\.connect\([^,\n]+,\s*\"([^\"]+)\"")
UNLOAD_THEN_SCOPED_LAUNCH_RE = re.compile(
    r"SceneTree\.unloadCurrentScene\(\)(?P<body>.{0,800}?)\bkanamaScope\.launch\s*\{",
    re.DOTALL,
)
# Ownership rules (Kanama task 97). The one rule lives in kanama
# docs/game-dev/godot-api.md "Resource Ownership": every RefCounted-typed return —
# create(), ResourceLoader.load…, and plain getters such as getAnimation()/getMesh() —
# is an owned +1 the caller closes. This audit therefore never flags closing an owned
# return (the regexes that did — BORROWED_ANIMATION_CLOSE_RE, ACTIVE_TWEEN_SCHEDULE_CLOSE_RE,
# GAMEPLAY_RESOURCE_CLOSE_RE — taught the opposite of the docs and forced real leaks).
# What it still rejects is the two cases the docs forbid:
#   - closing a *borrowed view* the script minted itself over a handle it already had
#     (Resource.fromHandle / X.fromObject), which releases a reference never taken;
#   - closing a *live Tween* (createTween(), or a field named after one), which must be
#     kill()ed through the Godot lifecycle instead. A Tweener is not a Tween: the
#     PropertyTweener/CallbackTweener a tweenProperty()/tweenCallback() call hands back is
#     an owned return and closing it is correct.
BORROWED_VIEW_CLOSE_RE = re.compile(
    r"\.from(?:Handle|Object)\s*\((?:[^()]|\([^()]*\))*\)\s*\??\s*\.\s*(?:use\s*\{|close\s*\()",
    re.DOTALL,
)
LIVE_TWEEN_CLOSE_RE = re.compile(
    r"(?:\.createTween\s*\([^)]*\)|\b(?P<name>(?!\w*tweener)\w*tween\w*))\??\s*\.\s*(?:use\s*\{|close\s*\()",
    re.IGNORECASE,
)

DUCK_TYPED_METHODS = {
    "collect_coin",
    "damage",
    "squash",
}

SMOKE_NAMES = {
    "Smoke.kt",
    "SmokeQuit.kt",
}

ALLOWED_RAW_CALLS = {
    # The original 3D Platformer keeps Audio as an autoload and calls
    # Audio.play(...) dynamically from gameplay scripts.
    ("Starter-Kit-3D-Platformer/kotlin-src/Brick.kt", "play"),
    ("Starter-Kit-3D-Platformer/kotlin-src/Coin.kt", "play"),
    ("Starter-Kit-3D-Platformer/kotlin-src/PlatformFalling.kt", "play"),
    ("Starter-Kit-3D-Platformer/kotlin-src/Player.kt", "play"),
}

ALLOWED_RAW_SIGNALS = {
    # These older audio pools connect to dynamically created players. They are
    # kept explicit until the ports are revisited more broadly.
    ("Starter-Kit-City-Builder/kotlin-src/Audio.kt", "finished"),
    ("Starter-Kit-FPS/kotlin-src/Audio.kt", "finished"),
    ("Starter-Kit-Match3/kotlin-src/Audio.kt", "finished"),
    # Viewport.size_changed has no generated wrapper in this demo project yet.
    ("Starter-Kit-Match3/kotlin-src/Main.kt", "size_changed"),
    # web/ mirrors of the accepted Match3 ports above (identical GDScript-derived
    # code; the Web API has no generated wrapper for these signals either).
    ("Starter-Kit-Match3/web/kotlin-src/Audio.kt", "finished"),
    ("Starter-Kit-Match3/web/kotlin-src/Main.kt", "size_changed"),
}

ALLOWED_STRING_CONNECTS = {
    ("Starter-Kit-Match3/kotlin-src/Main.kt", "center_grid_on_screen"),
    # web/ mirror of the accepted Match3 port above.
    ("Starter-Kit-Match3/web/kotlin-src/Main.kt", "center_grid_on_screen"),
}

ALLOWED_SMOKE_ENV_FILES = {
    # TPS smoke coverage needs hooks at the level/menu/death-part lifecycle
    # points that expose threaded load, robot death, and reload behavior.
    "tps-demo-kanama/kotlin-src/Level.kt",
    "tps-demo-kanama/kotlin-src/Menu.kt",
    "tps-demo-kanama/kotlin-src/Part.kt",
    "tps-demo-kanama/kotlin-src/PartDisappear.kt",
}


@dataclass(frozen=True)
class Finding:
    path: str
    line: int
    message: str


def relpath(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def is_smoke_file(path: Path) -> bool:
    return path.name in SMOKE_NAMES


def add_finding(findings: list[Finding], rel: str, text: str, offset: int, message: str) -> None:
    findings.append(Finding(rel, line_number(text, offset), message))


def audit_file(path: Path, root: Path) -> list[Finding]:
    text = path.read_text(encoding="utf-8")
    rel = relpath(path, root)
    smoke = is_smoke_file(path)
    findings: list[Finding] = []

    legacy_self_as = re.search(r"\bselfAs\s*\(\s*godotObject\s*,", text)
    if legacy_self_as:
        add_finding(findings, rel, text, legacy_self_as.start(), "legacy selfAs(godotObject, ::Type) should use KanamaScript.self or selfAs(::Type)")

    for pattern in ("ObjectCalls.emitSignal", "SignalInfo(", "SignalParam("):
        offset = text.find(pattern)
        if offset != -1:
            add_finding(findings, rel, text, offset, "outdated Kanama signal syntax should use typed signal helpers")

    if not smoke:
        for pattern in ("KANAMA_", "_SMOKE", "SMOKE_QUIT"):
            offset = text.find(pattern)
            if offset != -1:
                if rel not in ALLOWED_SMOKE_ENV_FILES:
                    add_finding(findings, rel, text, offset, "smoke/test environment logic belongs in Smoke*.kt")

        for pattern in ("kotlin.math", "java.util.Random", "kotlin.random.Random", "Random."):
            offset = text.find(pattern)
            if offset != -1:
                add_finding(findings, rel, text, offset, "demo ports should prefer Godot helpers when the original used Godot helpers")

        if re.search(r"companion\s+object\s*\{[^}]*\binstance\b", text, re.DOTALL):
            offset = text.find("companion object")
            add_finding(findings, rel, text, offset, "companion-object singleton facades are not allowed in gameplay scripts")

    for match in CALL_RE.finditer(text):
        method = match.group(1)
        if smoke:
            continue
        if method in DUCK_TYPED_METHODS:
            continue
        if (rel, method) in ALLOWED_RAW_CALLS:
            continue
        add_finding(findings, rel, text, match.start(), f'raw call("{method}") should use typed/generated API or be allowlisted')

    for match in SIGNAL_RE.finditer(text):
        signal = match.group(1)
        if smoke:
            continue
        if (rel, signal) in ALLOWED_RAW_SIGNALS:
            continue
        add_finding(findings, rel, text, match.start(), f'raw signal("{signal}") should use generated names or wrapper constants')

    for match in CONNECT_STRING_RE.finditer(text):
        method = match.group(1)
        if smoke:
            continue
        if (rel, method) in ALLOWED_STRING_CONNECTS:
            continue
        add_finding(findings, rel, text, match.start(), f'raw connect target "{method}" should use generated names or callback overload')

    for match in UNLOAD_THEN_SCOPED_LAUNCH_RE.finditer(text):
        if smoke:
            continue
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "work scheduled after SceneTree.unloadCurrentScene() must not use scene-owned kanamaScope; use MainThread.postAfterFrames",
        )

    for match in BORROWED_VIEW_CLOSE_RE.finditer(text):
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "fromHandle()/fromObject() mint a borrowed view over a handle you already hold; closing it releases a reference you never took (kanama docs/game-dev/godot-api.md#resource-ownership)",
        )

    for match in LIVE_TWEEN_CLOSE_RE.finditer(text):
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "a live Tween is engine-owned: kill() it, do not close() it (kanama docs/game-dev/godot-api.md#resource-ownership)",
        )

    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    root = args.root.resolve()
    files = sorted(root.glob("**/kotlin-src/**/*.kt"))
    findings: list[Finding] = []
    for path in files:
        if "/build/" in path.as_posix():
            continue
        findings.extend(audit_file(path, root))

    if findings:
        print("demo parity audit failed:", file=sys.stderr)
        for finding in findings:
            print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
        return 1

    print(f"[demo_parity_audit] PASS checked {len(files)} Kotlin demo script(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
