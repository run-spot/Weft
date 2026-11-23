#!/usr/bin/env python3
"""Small, dependency-free project profile and structural scaffold harness."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse


ROOT = Path(__file__).resolve().parents[1]
PROFILE_DIR = ROOT / "harness" / "profiles"
REQUIRED_DOCS = [
    "docs/direction.md",
    "docs/architecture/project-shapes.md",
    "docs/architecture/module-layout.md",
    "docs/architecture/apple-interop.md",
    "docs/architecture/bridge-library.md",
    "docs/architecture/backend.md",
    "docs/architecture/toolchain-policy.md",
    "docs/reference/runthespot.md",
    "docs/harness.md",
]
REQUIRED_SKILLS = [
    "weft-architect",
    "weft-scaffold",
    "weft-apple-bridge",
    "weft-review",
]
PROJECT_NAME_RE = re.compile(r"^[a-z][a-z0-9-]{1,62}$")
MARKDOWN_LINK_RE = re.compile(r"\]\(([^)]+)\)")
HANGUL_RE = re.compile(r"[\uac00-\ud7a3]")
REPOSITORY_TEXT_SUFFIXES = {".md", ".json", ".yml", ".yaml", ".py", ".sh"}


class WeftError(Exception):
    pass


def load_profiles() -> dict[str, dict[str, Any]]:
    profiles: dict[str, dict[str, Any]] = {}
    if not PROFILE_DIR.is_dir():
        raise WeftError(f"profile directory is missing: {PROFILE_DIR}")

    for path in sorted(PROFILE_DIR.glob("*.json")):
        try:
            profile = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise WeftError(f"cannot read profile {path.name}: {error}") from error

        required = {"schemaVersion", "id", "title", "description", "uiSharing", "backend", "modules"}
        missing = required - profile.keys()
        if missing:
            raise WeftError(f"{path.name} is missing fields: {', '.join(sorted(missing))}")
        if profile["schemaVersion"] != 1:
            raise WeftError(f"{path.name} uses unsupported schemaVersion {profile['schemaVersion']}")
        if profile["id"] in profiles:
            raise WeftError(f"duplicate profile id: {profile['id']}")
        if not isinstance(profile["modules"], list) or not profile["modules"]:
            raise WeftError(f"{path.name} must list one or more modules")

        seen_paths: set[str] = set()
        for module in profile["modules"]:
            if not isinstance(module, dict) or not {"path", "kind", "responsibility", "sourceSets"} <= module.keys():
                raise WeftError(f"{path.name} has an invalid module entry")
            module_path = Path(module["path"])
            if module_path.is_absolute() or ".." in module_path.parts or str(module_path) in seen_paths:
                raise WeftError(f"{path.name} has an unsafe or duplicate module path: {module['path']}")
            seen_paths.add(str(module_path))
            if not isinstance(module["sourceSets"], list):
                raise WeftError(f"{path.name}: {module['path']} sourceSets must be a list")
        profiles[profile["id"]] = profile

    if not profiles:
        raise WeftError("no profile manifests found")
    return profiles


def show_profiles(profiles: dict[str, dict[str, Any]]) -> None:
    for profile in profiles.values():
        print(f"{profile['id']} — {profile['title']}")
        print(f"  {profile['description']}")


def show_plan(profile: dict[str, Any]) -> None:
    print(f"# {profile['title']} ({profile['id']})")
    print()
    print(profile["description"])
    print()
    print(f"- UI sharing: {profile['uiSharing']}")
    print(f"- Backend: {profile['backend']}")
    print("- Toolchain: choose an officially compatible tuple; no versions are pinned by this plan.")
    print()
    print("## Modules")
    for module in profile["modules"]:
        print(f"- `{module['path']}` ({module['kind']}): {module['responsibility']}")
    print()
    print("This plan is a structural starting point. Read the matching architecture docs before adding build files.")


def write_module(root: Path, module: dict[str, Any]) -> None:
    module_root = root / module["path"]
    module_root.mkdir(parents=True, exist_ok=True)
    readme = [
        f"# {module['path']}",
        "",
        f"**Kind:** `{module['kind']}`",
        "",
        f"{module['responsibility']}",
        "",
        "This directory was generated as a structural scaffold. Add Gradle/Xcode configuration only after selecting and validating a toolchain tuple.",
    ]
    if module["sourceSets"]:
        readme.extend(["", "## Planned source sets", ""])
        readme.extend(f"- `{source_set}`" for source_set in module["sourceSets"])
        for source_set in module["sourceSets"]:
            (module_root / "src" / source_set).mkdir(parents=True, exist_ok=True)
    (module_root / "README.md").write_text("\n".join(readme) + "\n", encoding="utf-8")


def scaffold(profile: dict[str, Any], name: str, output: Path) -> None:
    if not PROJECT_NAME_RE.fullmatch(name):
        raise WeftError("project name must be 2–63 lowercase letters, digits, or hyphens and start with a letter")

    target = output.expanduser().resolve()
    if target == ROOT or target in ROOT.parents:
        raise WeftError("output must not overwrite or contain the Weft toolkit repository")
    if target.exists() and any(target.iterdir()):
        raise WeftError(f"output directory is not empty: {target}")

    target.mkdir(parents=True, exist_ok=True)
    for module in profile["modules"]:
        write_module(target, module)

    module_lines = [f"- `{module['path']}`: {module['responsibility']}" for module in profile["modules"]]
    manifest = [
        "# Weft project manifest",
        "",
        f"project: {name}",
        f"profile: {profile['id']}",
        "generator_version: 0.1.0",
        "ui_sharing: " + profile["uiSharing"],
        "backend: " + profile["backend"],
        "toolchain_status: undecided",
        "",
        "## Decisions to finish",
        "",
        "- Select an exact Kotlin/Gradle/AGP/Compose/Xcode tuple using the Weft toolchain policy.",
        "- Add build scripts and CI, then verify Android and iOS targets before calling this project build-ready.",
        "- Record platform-specific requirements and any deviations from this profile.",
        "",
        "## Modules",
        "",
        *module_lines,
    ]
    (target / "WEFT.md").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    (target / "README.md").write_text(
        f"# {name}\n\nGenerated with Weft profile `{profile['id']}`.\n\n"
        "This is a structural scaffold, not a build-ready app. See `WEFT.md` for the decisions still required.\n",
        encoding="utf-8",
    )
    print(f"Created structural scaffold at {target}")
    print("Next: review WEFT.md, select a verified toolchain tuple, then add build configuration.")


def verify_skills() -> list[str]:
    errors: list[str] = []
    for skill_name in REQUIRED_SKILLS:
        skill_file = ROOT / "skills" / skill_name / "SKILL.md"
        if not skill_file.is_file():
            errors.append(f"missing skill entrypoint: {skill_file.relative_to(ROOT)}")
            continue
        text = skill_file.read_text(encoding="utf-8")
        match = re.match(r"^---\s*\n(.*?)\n---\s*\n", text, re.DOTALL)
        if not match:
            errors.append(f"invalid YAML frontmatter: {skill_file.relative_to(ROOT)}")
            continue
        frontmatter = match.group(1)
        name_match = re.search(r"^name:\s*['\"]?([^'\"\n]+)", frontmatter, re.MULTILINE)
        description_match = re.search(r"^description:\s*['\"]?([^'\"\n]+)", frontmatter, re.MULTILINE)
        if not name_match or name_match.group(1).strip() != skill_name:
            errors.append(f"skill name does not match directory: {skill_file.relative_to(ROOT)}")
        if not description_match or "TODO" in description_match.group(1) or len(description_match.group(1).strip()) < 20:
            errors.append(f"skill description is missing or too short: {skill_file.relative_to(ROOT)}")
        if "[TODO" in text:
            errors.append(f"unfinished skill placeholder: {skill_file.relative_to(ROOT)}")
    return errors


def verify_toolkit(profiles: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for path in REQUIRED_DOCS:
        if not (ROOT / path).is_file():
            errors.append(f"missing required document: {path}")
    errors.extend(verify_skills())
    errors.extend(verify_relative_markdown_links())
    errors.extend(verify_repository_language())
    required_profiles = {"kmp-core-native-ui", "cmp-client", "cmp-spring-service"}
    missing_profiles = required_profiles - set(profiles)
    if missing_profiles:
        errors.append(f"missing starter profiles: {sorted(missing_profiles)}")
    return errors


def verify_repository_language() -> list[str]:
    errors: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts or path.suffix.lower() not in REPOSITORY_TEXT_SUFFIXES:
            continue
        if HANGUL_RE.search(path.read_text(encoding="utf-8")):
            errors.append(f"repository text must be English: {path.relative_to(ROOT)}")
    return errors


def verify_relative_markdown_links() -> list[str]:
    errors: list[str] = []
    for markdown_file in ROOT.rglob("*.md"):
        if ".git" in markdown_file.parts:
            continue
        source = markdown_file.read_text(encoding="utf-8")
        for raw_link in MARKDOWN_LINK_RE.findall(source):
            link = raw_link.strip().split(maxsplit=1)[0].strip("<>")
            parsed = urlparse(link)
            if parsed.scheme or link.startswith("#"):
                continue
            relative_path = unquote(parsed.path)
            if not relative_path:
                continue
            destination = (markdown_file.parent / relative_path).resolve()
            if not destination.exists():
                relative_source = markdown_file.relative_to(ROOT)
                errors.append(f"broken Markdown link in {relative_source}: {link}")
    return errors


def verify_project(project_path: Path, profiles: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    project = project_path.expanduser().resolve()
    manifest_path = project / "WEFT.md"
    if not manifest_path.is_file():
        return [f"missing project manifest: {manifest_path}"]
    manifest = manifest_path.read_text(encoding="utf-8")
    profile_match = re.search(r"^profile:\s*(\S+)\s*$", manifest, re.MULTILINE)
    if not profile_match or profile_match.group(1) not in profiles:
        return ["WEFT.md has a missing or unknown profile id"]
    profile = profiles[profile_match.group(1)]
    for module in profile["modules"]:
        module_path = project / module["path"]
        if not module_path.is_dir():
            errors.append(f"missing module directory: {module['path']}")
        if not (module_path / "README.md").is_file():
            errors.append(f"missing module README: {module['path']}/README.md")
        for source_set in module["sourceSets"]:
            source_path = module_path / "src" / source_set
            if not source_path.is_dir():
                errors.append(f"missing planned source set: {module['path']}/src/{source_set}")
    if not re.search(r"^toolchain_status:\s*\S+", manifest, re.MULTILINE):
        errors.append("WEFT.md must record a toolchain status")
    project_readme = project / "README.md"
    if not project_readme.is_file():
        errors.append("missing project README.md")
    elif "structural scaffold" not in project_readme.read_text(encoding="utf-8"):
        errors.append("project README must state whether this scaffold is build-ready")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(prog="weft", description="Plan and scaffold KMP/CMP project shapes.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("profiles", help="list available architecture profiles")

    plan_parser = subparsers.add_parser("plan", help="print a profile plan without writing files")
    plan_parser.add_argument("--profile", required=True)

    scaffold_parser = subparsers.add_parser("scaffold", help="create a structural project scaffold")
    scaffold_parser.add_argument("--profile", required=True)
    scaffold_parser.add_argument("--name", required=True)
    scaffold_parser.add_argument("--output", required=True, type=Path)

    verify_parser = subparsers.add_parser("verify", help="validate Weft or a generated project")
    verify_parser.add_argument("--project", type=Path)

    args = parser.parse_args()
    try:
        profiles = load_profiles()
        if args.command == "profiles":
            show_profiles(profiles)
        elif args.command == "plan":
            if args.profile not in profiles:
                raise WeftError(f"unknown profile: {args.profile}")
            show_plan(profiles[args.profile])
        elif args.command == "scaffold":
            if args.profile not in profiles:
                raise WeftError(f"unknown profile: {args.profile}")
            scaffold(profiles[args.profile], args.name, args.output)
        elif args.command == "verify":
            errors = verify_toolkit(profiles)
            if args.project:
                errors.extend(verify_project(args.project, profiles))
            if errors:
                print("Weft verification failed:", file=sys.stderr)
                for error in errors:
                    print(f"- {error}", file=sys.stderr)
                return 1
            print("Weft verification passed")
    except WeftError as error:
        print(f"weft: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
