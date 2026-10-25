#!/usr/bin/env python3
"""Audit Kanama demo ports for patterns that commonly drift from GDScript.

This is intentionally conservative: it fails on new risky patterns, but
allowlists places where the original demos are genuinely dynamic, such as
Godot-style damage/squash duck typing and smoke-only probes.

`./gradlew check` runs this audit (with the two Kanama guardrail audits) on every
push and pull request through `.github/workflows/check.yml`; nothing here needs
Godot or compiled scripts.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


CALL_RE = re.compile(r"\.call\(\s*\"([^\"]+)\"")
SIGNAL_RE = re.compile(r"\.signal\(\s*\"([^\"]+)\"")
CONNECT_STRING_RE = re.compile(r"\.connect\([^,\n]+,\s*\"([^\"]+)\"")
UNLOAD_THEN_SCOPED_LAUNCH_RE = re.compile(
    r"SceneTree\.unloadCurrentScene\(\)(?P<body>.{0,800}?)(?<![\w.])(?:scriptScope\.)?launch\s*\{",
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
#     (Resource.fromHandle), which releases nothing since Kanama task 132 and was a
#     reference never taken before. The `from*` downcasts (X.fromObject, X.fromResource)
#     are no longer borrowed: since task 132 each takes a reference of its own, so closing
#     one is correct and is not flagged;
#   - closing a *live Tween* (createTween(), or a field named after one), which must be
#     kill()ed through the Godot lifecycle instead. A Tweener is not a Tween: the
#     PropertyTweener/CallbackTweener a tweenProperty()/tweenCallback() call hands back is
#     an owned return and closing it is correct.
BORROWED_VIEW_CLOSE_RE = re.compile(
    r"\.fromHandle\s*\((?:[^()]|\([^()]*\))*\)\s*\??\s*\.\s*(?:use\s*\{|close\s*\()",
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
    "SmokeTouchSwipe.kt",
}

# Gameplay classes keep no smoke or probe hooks (Kanama task 131 item 17): every hook lives in the demo's
# Smoke.kt autoload. These patterns catch the ways one creeps back into a gameplay class: a member named after
# the hook (also as an extension function on the class, or backticked), a @GodotName string naming it, and any
# read of the environment or the command line that gates it.
SMOKE_MEMBER_RE = re.compile(
    r"\b(?:fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.?]+\.)?(?P<name>`[^`\n]*(?:smoke|probe)[^`\n]*`|\w*(?:smoke|probe)\w*)",
    re.IGNORECASE,
)
GODOT_NAME_RE = re.compile(r"@GodotName\(\s*(?:name\s*=\s*)?\"(?P<name>[^\"]*(?:smoke|probe)[^\"]*)\"", re.IGNORECASE)
ENV_READ_RE = re.compile(
    r"\b(?:System\.getenv|(?<![\w.])getenv\s*\(|ProcessBuilder|(?:OS\.)?(?:getEnvironment|hasEnvironment)|(?:OS\.)?(?:getCmdlineUserArgs|getCmdlineArgs|cmdlineUserArgs|cmdlineArgs))(?!\w)"
)
# A gameplay class must not know the Smoke autoload at all: the hooks reach the gameplay, never the other way round.
SMOKE_AUTOLOAD_REF_RE = re.compile(r"\bSmoke\b")
# String literals or comments; blank_comments() keeps the former and blanks the latter (same offsets).
STRING_OR_COMMENT_RE = re.compile(r'"(?:\\.|[^"\\\n])*"|//[^\n]*|/\*.*?\*/', re.DOTALL)
# The in-game smoke EFFECT (puffs of smoke when a bot dies), not a test hook.
SMOKE_EFFECT_FILES = {"SmokePuff.kt"}
SMOKE_EFFECT_NAMES = {"SMOKE_PUFF"}

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

ALLOWED_KOTLIN_MATH = {
    # The benchmark's own contract is that nothing in its loop crosses the engine boundary: it measures the language
    # runtime's floating-point throughput, and Mathf.sin and Mathf.cos call into Godot. The upstream Kotlin uses
    # kotlin.math for the same reason.
    "Bunnymark/kotlin-src/BunnymarkComputationKanama.kt",
}

ALLOWED_STRING_CONNECTS = {
    ("Starter-Kit-Match3/kotlin-src/Main.kt", "center_grid_on_screen"),
    # web/ mirror of the accepted Match3 port above.
    ("Starter-Kit-Match3/web/kotlin-src/Main.kt", "center_grid_on_screen"),
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


def blank_comments(text: str) -> str:
    """The source with every comment replaced by spaces (newlines kept), so a rule can read code and strings only."""

    def blank(match: re.Match[str]) -> str:
        token = match.group(0)
        return token if token.startswith('"') else re.sub(r"[^\n]", " ", token)

    return STRING_OR_COMMENT_RE.sub(blank, text)


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
                add_finding(findings, rel, text, offset, "smoke/test environment logic belongs in Smoke*.kt")

        if path.name not in SMOKE_EFFECT_FILES:
            for match in ENV_READ_RE.finditer(text):
                add_finding(findings, rel, text, match.start(), "environment/command-line reads belong in Smoke.kt, not in a gameplay class")
            for match in SMOKE_AUTOLOAD_REF_RE.finditer(blank_comments(text)):
                add_finding(findings, rel, text, match.start(), "a gameplay class must not reference the Smoke autoload; Smoke.kt reaches gameplay, not the reverse")
            for match in GODOT_NAME_RE.finditer(text):
                add_finding(findings, rel, text, match.start(), f"@GodotName(\"{match.group('name')}\") names a smoke/probe hook; it belongs in Smoke.kt")
            for match in SMOKE_MEMBER_RE.finditer(text):
                if match.group("name") not in SMOKE_EFFECT_NAMES:
                    add_finding(findings, rel, text, match.start(), f"smoke/probe member '{match.group('name')}' belongs in Smoke.kt, not in a gameplay class")

        # `Random.` as a word of its own (Kotlin's `Random.nextInt()`), not the end of a name such as `spawnRandom.seed`.
        for pattern in (r"kotlin\.math", r"java\.util\.Random", r"kotlin\.random\.Random", r"(?<![\w.])Random\."):
            if pattern == r"kotlin\.math" and rel in ALLOWED_KOTLIN_MATH:
                continue
            match = re.search(pattern, text)
            offset = match.start() if match else -1
            if offset != -1:
                add_finding(findings, rel, text, offset, "demo ports should prefer Godot helpers when the original used Godot helpers")

        if re.search(r"companion\s+object\s*\{[^}]*\binstance\b", text, re.DOTALL):
            offset = text.find("companion object")
            add_finding(findings, rel, text, offset, "companion-object singleton facades are not allowed in gameplay scripts")

    # justified (every `if smoke: continue` and allowlist `continue` in the loops below): Smoke*.kt scripts and the
    # explicit ALLOWED_* tables are exempt from these rules by design; a new exemption is a reviewed diff to the table.
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
            "work scheduled after SceneTree.unloadCurrentScene() must not use the scene-owned script scope (launch); use MainThread.postAfterFrames",
        )

    for match in BORROWED_VIEW_CLOSE_RE.finditer(text):
        add_finding(
            findings,
            rel,
            text,
            match.start(),
            "fromHandle() mints a borrowed view over a handle you already hold; closing it releases nothing (kanama docs/game-dev/godot-api.md#resource-ownership)",
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


def has_smoke_quit(demo_dir: Path) -> bool:
    """The iOS runner's rule (scripts/demo_has_smoke_quit.sh): a Smoke*.kt reads KANAMA_DEMO_SMOKE_QUIT."""
    helper = Path(__file__).resolve().parent / "demo_has_smoke_quit.sh"
    return subprocess.run(["bash", str(helper), str(demo_dir)], capture_output=True).returncode == 0


def smoke_quit_detection_failures() -> list[str]:
    """Red run of the runner's detection: Smoke.kt and the old SmokeQuit.kt are found by the variable, nothing else is."""
    import tempfile

    shapes = {
        "Smoke.kt reading the variable": ("Smoke.kt", 'System.getenv("KANAMA_DEMO_SMOKE_QUIT")', True),
        "SmokeQuit.kt reading the variable": ("SmokeQuit.kt", 'System.getenv("KANAMA_DEMO_SMOKE_QUIT")', True),
        "Smoke.kt reading another variable": ("Smoke.kt", 'OS.getEnvironment("KANAMA_FPS_SMOKE")', False),
        "gameplay file reading the variable": ("Main.kt", 'System.getenv("KANAMA_DEMO_SMOKE_QUIT")', False),
    }
    failures = []
    for name, (file_name, source, expected) in shapes.items():
        with tempfile.TemporaryDirectory() as tmp:
            demo = Path(tmp)
            (demo / "kotlin-src").mkdir()
            (demo / "kotlin-src" / file_name).write_text(source, encoding="utf-8")
            if has_smoke_quit(demo) != expected:
                failures.append(f"iOS smoke detection: '{name}' should be {'detected' if expected else 'ignored'}")
    return failures


def runner_smoke_demo_failures(root: Path) -> list[str]:
    """Every demo desktop_smoke_all.sh runs with KANAMA_DEMO_SMOKE_QUIT=1 must be detected by the iOS runner too."""
    script = root / "scripts" / "desktop_smoke_all.sh"
    if not script.is_file():
        return []
    folders = re.findall(r'^run_smoke "([^"]+)" KANAMA_DEMO_SMOKE_QUIT=1', script.read_text(encoding="utf-8"), re.MULTILINE)
    failures = [f"desktop_smoke_all.sh runs no KANAMA_DEMO_SMOKE_QUIT=1 demo; the pattern is stale"] if not folders else []
    for folder in folders:
        if not has_smoke_quit(root / folder):
            failures.append(
                f"{folder}: desktop_smoke_all.sh runs it with KANAMA_DEMO_SMOKE_QUIT=1 but no kotlin-src/Smoke*.kt reads the "
                "variable, so the iOS runner would launch it without the smoke and stay green"
            )
    return failures


def self_test() -> int:
    """Red run of the smoke-hook rules: a gameplay class with each hook shape must fail, a clean one must pass."""
    import tempfile

    cases = {
        "env gate": 'class Level { fun ready() { if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") == "1") return } }',
        "env read": 'class Level { fun ready() { val x = OS.getEnvironment("ANYTHING") } }',
        "probe member": "class Main { fun differentialProbe(): String = \"\" }",
        "smoke member": "class Main { fun smokeStartGame() {} }",
        "hasEnvironment gate": 'class Level { fun ready() { if (OS.hasEnvironment("ANYTHING")) return } }',
        "cmdline user args gate": 'class Level { fun ready() { if ("--smoke" in OS.getCmdlineUserArgs()) return } }',
        "cmdline args gate": 'class Level { fun ready() { val a = OS.getCmdlineArgs() } }',
        "godot name probe": 'class Main { @GodotName("differential_probe") fun dump() {} }',
        "godot name smoke": 'class Main { @GodotName(name = "smoke_hook") fun dump() {} }',
        "extension function": "fun Main.smokeHook() {}",
        "extension property": "val Main.probeState: Int get() = 0",
        "backticked name": "class Main { fun `smoke hook`() {} }",
        "smoke autoload reference": "class Main { fun ready() { Smoke.start() } }",
        "smoke autoload node path": 'class Main { fun ready() { val s = self.getNode("/root/Smoke") } }',
        "bare getenv": "import java.lang.System.*\nclass Level { fun ready() { val x = getenv(\"ANYTHING\") } }",
        "process environment": "class Level { fun ready() { val x = ProcessBuilder().environment() } }",
        "kotlin random": "class Level { fun ready() { val x = Random.nextInt() } }",
    }
    # A name ending in Random (a RandomNumberGenerator field) is not Kotlin's Random.
    clean = "class Main {\n    // Read by Smoke: a comment may name the harness\n    fun ready() { val reflection = 1; spawnRandom.seed = 1 }\n}"
    failures = []
    for name, source in {**cases, "clean": clean}.items():
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            target = root / "Demo" / "kotlin-src" / "Main.kt"
            target.parent.mkdir(parents=True)
            target.write_text(source, encoding="utf-8")
            found = audit_file(target, root)
            if name == "clean" and found:
                failures.append(f"clean source was flagged: {found}")
            if name != "clean" and not found:
                failures.append(f"'{name}' in a gameplay class was NOT flagged")
            smoke_copy = root / "Demo" / "kotlin-src" / "Smoke.kt"
            smoke_copy.write_text(source, encoding="utf-8")
            if name != "clean" and any("belongs in" in f.message for f in audit_file(smoke_copy, root)):
                failures.append(f"'{name}' in Smoke.kt was flagged but is allowed there")
    failures.extend(smoke_quit_detection_failures())
    if failures:
        print("demo parity audit self-test FAILED:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1
    print(f"[demo_parity_audit] self-test PASS ({len(cases)} smoke-hook shapes flagged, clean source and Smoke.kt accepted)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--self-test", action="store_true", help="prove the smoke-hook rules fail on a bad gameplay class")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

    root = args.root.resolve()
    files = sorted(root.glob("**/kotlin-src/**/*.kt"))
    if not files:
        print(f"demo parity audit: no kotlin-src/**/*.kt under {root}; refusing a vacuous pass", file=sys.stderr)
        return 2
    findings: list[Finding] = []
    for path in files:
        if "/build/" in path.as_posix():
            continue  # justified: generated build output is not demo source
        findings.extend(audit_file(path, root))

    runner_failures = runner_smoke_demo_failures(root)
    if findings or runner_failures:
        print("demo parity audit failed:", file=sys.stderr)
        for finding in findings:
            print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
        for failure in runner_failures:
            print(failure, file=sys.stderr)
        return 1

    print(f"[demo_parity_audit] PASS checked {len(files)} Kotlin demo script(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
