#!/usr/bin/env python3
"""Audit Kanama demo ports for patterns that commonly drift from GDScript.

This is intentionally conservative: it fails on new risky patterns, but
allowlists places where the original demos are genuinely dynamic, such as
Godot-style damage/squash duck typing and smoke-only probes.
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
BORROWED_ANIMATION_CLOSE_RE = re.compile(
    r"\.getAnimation\s*\([^)]*\)\s*\??\.\s*(?:use\s*\{|close\s*\()",
    re.DOTALL,
)
ACTIVE_TWEEN_SCHEDULE_CLOSE_RE = re.compile(
    r"\.tween(?:Property|Method|Callback)\s*\([^)]*\)\s*\??\s*(?:\.let\s*\{[^}]*?\bclose\s*\(\)|\.close\s*\()",
    re.DOTALL,
)
GAMEPLAY_RESOURCE_CLOSE_RE = re.compile(
    r"\b(?P<name>\w*(?:tween|mesh|scene|stream|material|texture|animation|resource)\w*)\??\s*\.close\s*\(",
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
}

ALLOWED_STRING_CONNECTS = {
    ("Starter-Kit-Match3/kotlin-src/Main.kt", "center_grid_on_screen"),
}

ALLOWED_RESOURCE_CLOSES = {
    # The Bunnymark harness owns these ResourceLoader-created texture wrappers
    # for the benchmark scene lifetime and releases them on exit.
    ("Bunnymark/kotlin-src/BunnymarkV1DrawTextureKanama.kt", "bunnyTexture"),
    ("Bunnymark/kotlin-src/BunnymarkV1SpritesKanama.kt", "bunnyTexture"),
    ("Bunnymark/kotlin-src/BunnymarkV2Kanama.kt", "bunnyTexture"),
    ("Bunnymark/kotlin-src/BunnymarkV3Kanama.kt", "bunnyTexture"),
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

    for match in BORROWED_ANIMATION_CLOSE_RE.finditer(text):
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "AnimationPlayer.getAnimation() returns a scene-owned animation; do not close/use-wrap it to silence shutdown warnings",
        )

    for match in ACTIVE_TWEEN_SCHEDULE_CLOSE_RE.finditer(text):
        if smoke:
            continue
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "active Tween/Tweener returned by tweenProperty/tweenMethod/tweenCallback must not be closed immediately; let Godot run it and close only after finish/kill if tracked",
        )

    for match in GAMEPLAY_RESOURCE_CLOSE_RE.finditer(text):
        if smoke:
            continue
        if (rel, match.group("name")) in ALLOWED_RESOURCE_CLOSES:
            continue
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "gameplay scripts should not close resource/tween-like Godot values; use Godot lifecycle APIs or add a narrow audit allowlist for caller-owned interop values",
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
