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
    "docs/architecture/dependency-policy.md",
    "docs/architecture/bridge-library.md",
    "docs/architecture/backend.md",
    "docs/architecture/toolchain-policy.md",
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
DEPENDENCY_POLICY_PATH = ROOT / "harness" / "dependency-policy.json"
GROOVY_DEPENDENCY_RE = re.compile(
    r"\b(?:api|implementation|compileOnly|runtimeOnly|[A-Za-z0-9_]*(?:Api|Implementation|CompileOnly|RuntimeOnly))\s+(['\"])([^'\"]+)\1"
)
ADD_DEPENDENCY_RE = re.compile(r"\b(dependencies\s*\.\s*)?add(?:Provider|AllLater|All)?\s*\(")
COORDINATE_RE = re.compile(r"(?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+)")
REPOSITORY_TEXT_SUFFIXES = {".md", ".json", ".yml", ".yaml", ".py", ".sh"}
GENERATED_PATH_PARTS = {".git", ".gradle", "build", "checkouts"}
REQUIRED_SKILL_RULES = {
    "weft-architect": (
        "dependency-policy.md",
        "hard allowlist",
        "UIApplicationDelegate",
        "app UI, state, and business behavior in Kotlin",
        "policy blocker",
        "Kotlin/Swift ownership boundaries",
        "A backend choice is mandatory: Spring Boot or Supabase",
        "PKCE itself does not require an application backend",
        "If the team will operate a separate server, choose Spring Boot; otherwise choose Supabase",
    ),
    "weft-scaffold": (
        "dependency-policy.md",
        "strict direct-dependency allowlist",
        "ios_implementation: kotlin-first",
        "ask whether the team will operate a separate server",
        "do not treat PKCE itself as requiring a backend",
    ),
    "weft-apple-bridge": (
        "dependency-policy.md",
        "UIApplicationDelegate",
        "Objective-C-visible",
        "run.thespot.weft:weft-*",
        "widgets/extensions",
    ),
    "weft-review": (
        "dependency-policy.md",
        "unapproved or unparsed dependency",
        "blocking policy violation",
        "Kotlin owns iOS app startup",
        "Objective-C-visible SDK façade",
        "Inventory every Swift source file",
        "Every Weft app must choose Spring Boot or Supabase",
        "OAuth 2.0 Authorization Code with PKCE",
    ),
}


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
        if profile["backend"] not in {"spring-boot", "supabase"}:
            raise WeftError(f"{path.name} backend must be spring-boot or supabase")
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
        module_kinds = {module["kind"] for module in profile["modules"]}
        if profile["backend"] == "supabase" and "supabase-project" not in module_kinds:
            raise WeftError(f"{path.name} must include a supabase-project module")
        if profile["backend"] == "spring-boot" and "spring-boot-application" not in module_kinds:
            raise WeftError(f"{path.name} must include a spring-boot-application module")
        if profile["backend"] == "supabase" and "spring-boot-application" in module_kinds:
            raise WeftError(f"{path.name} cannot combine Supabase with a Spring Boot backend")
        if profile["backend"] == "spring-boot" and "supabase-project" in module_kinds:
            raise WeftError(f"{path.name} cannot combine Spring Boot with a Supabase backend")
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
    print("- Authentication: OAuth 2.0 Authorization Code with PKCE")
    print("- Dependency policy: strict Weft/Montage/networking allowlist")
    print("- iOS implementation: Kotlin-first; Swift is limited to Xcode bootstrap, widgets/extensions, and SDK façades")
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
    ]
    if module["kind"] == "supabase-project":
        readme.extend(
            [
                "This directory is a structural scaffold, not a deployable Supabase project. Add and validate Supabase CLI configuration, migrations, policies, and Edge Functions before deployment.",
                "",
                "Never commit access tokens, database passwords, OAuth client secrets, or Supabase secret/service-role keys. Use ignored local environment files and deployment secret storage.",
            ]
        )
    else:
        readme.append(
            "This directory was generated as a structural scaffold. Add Gradle/Xcode configuration only after selecting and validating a toolchain tuple."
        )
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
        "auth_flow: oauth2-authorization-code-pkce",
        "dependency_policy: strict-direct-dependency-allowlist",
        "ios_implementation: kotlin-first",
        "toolchain_status: undecided",
        "",
        "## Decisions to finish",
        "",
        "- Select an exact Kotlin/Gradle/AGP/Compose/Xcode tuple using the Weft toolchain policy.",
        "- Add build scripts and CI, then verify Android and iOS targets before calling this project build-ready.",
        "- Record the PKCE authorization issuer, app callback URLs, verifier/token storage boundary, and API token validation owner.",
        "- Provision credentials through the selected backend's deployment secret store; never commit production secrets.",
        "- Keep app startup, UIApplicationDelegate-level behavior, app UI, and business logic in Kotlin; limit Swift app code to an Xcode bootstrap/forwarder.",
        "- Record any Swift widget/extension or Objective-C-visible SwiftPM SDK façade as a narrow platform exception.",
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
        for rule in REQUIRED_SKILL_RULES.get(skill_name, ()):
            if rule.casefold() not in text.casefold():
                errors.append(f"skill is missing required Weft policy rule '{rule}': {skill_file.relative_to(ROOT)}")
    return errors


def verify_toolkit(profiles: dict[str, dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    for path in REQUIRED_DOCS:
        if not (ROOT / path).is_file():
            errors.append(f"missing required document: {path}")
    if not DEPENDENCY_POLICY_PATH.is_file():
        errors.append("missing dependency allowlist: harness/dependency-policy.json")
    errors.extend(verify_skills())
    errors.extend(verify_relative_markdown_links())
    errors.extend(verify_repository_language())
    required_profiles = {
        "kmp-core-supabase",
        "kmp-core-spring-service",
        "cmp-supabase",
        "cmp-spring-service",
    }
    missing_profiles = required_profiles - set(profiles)
    if missing_profiles:
        errors.append(f"missing starter profiles: {sorted(missing_profiles)}")
    expected_matrix = {
        ("kotlin-platform", "spring-boot"),
        ("kotlin-platform", "supabase"),
        ("compose", "spring-boot"),
        ("compose", "supabase"),
    }
    actual_matrix = {(profile["uiSharing"], profile["backend"]) for profile in profiles.values()}
    if not expected_matrix <= actual_matrix:
        errors.append("starter profiles must cover both UI-sharing modes with Spring Boot and Supabase")
    return errors


def verify_repository_language() -> list[str]:
    errors: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or is_generated_path(path) or path.suffix.lower() not in REPOSITORY_TEXT_SUFFIXES:
            continue
        if HANGUL_RE.search(path.read_text(encoding="utf-8")):
            errors.append(f"repository text must be English: {path.relative_to(ROOT)}")
    return errors


def is_generated_path(path: Path) -> bool:
    """Exclude build outputs and external checkouts from repository source checks."""
    try:
        relative_path = path.relative_to(ROOT)
    except ValueError:
        return False
    return any(part in GENERATED_PATH_PARTS for part in relative_path.parts)


def verify_relative_markdown_links() -> list[str]:
    errors: list[str] = []
    for markdown_file in ROOT.rglob("*.md"):
        if is_generated_path(markdown_file):
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
    backend_match = re.search(r"^backend:\s*(\S+)\s*$", manifest, re.MULTILINE)
    if not backend_match or backend_match.group(1) != profile["backend"]:
        errors.append("WEFT.md backend must match the selected profile")
    if not re.search(r"^auth_flow:\s*oauth2-authorization-code-pkce\s*$", manifest, re.MULTILINE):
        errors.append("WEFT.md must record OAuth 2.0 Authorization Code with PKCE")
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
    if not re.search(r"^dependency_policy:\s*strict-direct-dependency-allowlist\s*$", manifest, re.MULTILINE):
        errors.append("WEFT.md must adopt the strict direct-dependency allowlist")
    if not re.search(r"^ios_implementation:\s*kotlin-first\s*$", manifest, re.MULTILINE):
        errors.append("WEFT.md must adopt Kotlin-first iOS app implementation")
    project_readme = project / "README.md"
    if not project_readme.is_file():
        errors.append("missing project README.md")
    elif "structural scaffold" not in project_readme.read_text(encoding="utf-8"):
        errors.append("project README must state whether this scaffold is build-ready")
    errors.extend(verify_project_dependencies(project, profile))
    return errors


def normalize_catalog_alias(alias: str) -> str:
    return re.sub(r"[-_.]+", ".", alias).strip(".")


def load_catalog_coordinates(project: Path) -> dict[str, tuple[str, str]]:
    aliases: dict[str, tuple[str, str]] = {}
    catalog_path = project / "gradle" / "libs.versions.toml"
    if not catalog_path.is_file():
        return aliases

    in_libraries = False
    for line in catalog_path.read_text(encoding="utf-8").splitlines():
        section = re.match(r"^\s*\[([^]]+)\]\s*$", line)
        if section:
            in_libraries = section.group(1) == "libraries"
            continue
        if not in_libraries:
            continue

        entry = re.match(r"^\s*([A-Za-z0-9_.-]+)\s*=\s*(.+?)\s*$", line)
        if not entry:
            continue
        alias, value = entry.groups()
        module = re.search(r"\bmodule\s*=\s*['\"]([^'\"]+)['\"]", value)
        if module:
            coordinate = COORDINATE_RE.search(module.group(1))
        else:
            group = re.search(r"\bgroup\s*=\s*['\"]([^'\"]+)['\"]", value)
            artifact = re.search(r"\bname\s*=\s*['\"]([^'\"]+)['\"]", value)
            coordinate = None
            if group and artifact:
                aliases[normalize_catalog_alias(alias)] = (group.group(1), artifact.group(1))
                continue
            coordinate = COORDINATE_RE.search(value.strip("'\""))
        if coordinate:
            aliases[normalize_catalog_alias(alias)] = (
                coordinate.group("group"),
                coordinate.group("artifact"),
            )
    return aliases


def coordinate_is_approved(group: str, artifact: str, policy: dict[str, Any]) -> bool:
    for rule in policy.get("allowedCoordinates", []):
        if "groupPrefix" in rule:
            group_matches = group.startswith(rule["groupPrefix"])
        else:
            group_matches = group == rule.get("group")
        if not group_matches:
            continue
        allowed_artifacts = rule.get("artifacts", [])
        artifact_prefix = rule.get("artifactPrefix")
        if artifact in allowed_artifacts or (artifact_prefix and artifact.startswith(artifact_prefix)):
            return True
    return False


def read_call_argument(source: str, open_parenthesis: int) -> str | None:
    depth = 0
    quote: str | None = None
    escaped = False
    start = open_parenthesis + 1
    for index in range(open_parenthesis, len(source)):
        character = source[index]
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character in ("'", '"'):
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
            if depth == 0:
                return source[start:index]
    return None


def split_top_level_arguments(arguments: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for index, character in enumerate(arguments):
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character in ("'", '"'):
            quote = character
        elif character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
        elif character == "," and depth == 0:
            parts.append(arguments[start:index].strip())
            start = index + 1
    trailing = arguments[start:].strip()
    if trailing:
        parts.append(trailing)
    return parts


def is_dependency_configuration(configuration: str) -> bool:
    return configuration in {"api", "implementation", "compileOnly", "runtimeOnly", "ksp"} or bool(
        re.fullmatch(r"[A-Za-z0-9_]*(?:Api|Implementation|CompileOnly|RuntimeOnly)", configuration)
    )


def is_local_project_dependency(argument: str) -> bool:
    source = argument.strip()
    call = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(", source)
    if not call:
        return False
    opening_parenthesis = call.end() - 1
    contents = read_call_argument(source, opening_parenthesis)
    if contents is None:
        return False
    closing_parenthesis = opening_parenthesis + len(contents) + 1
    if source[closing_parenthesis] != ")" or source[closing_parenthesis + 1 :].strip():
        return False
    if call.group(1) == "project":
        return True
    if call.group(1) in {"platform", "enforcedPlatform", "testFixtures"}:
        return is_local_project_dependency(contents)
    return False


def verify_dependency_argument(
    argument: str,
    relative: Path,
    aliases: dict[str, tuple[str, str]],
    policy: dict[str, Any],
) -> list[str]:
    errors: list[str] = []
    if is_local_project_dependency(argument):
        return errors
    if re.fullmatch(r"\s*kotlin\s*\(\s*['\"]test(?:-[A-Za-z0-9_-]+)?['\"]\s*\)\s*", argument):
        return errors

    coordinates = list(COORDINATE_RE.finditer(argument))
    if coordinates:
        for coordinate in coordinates:
            group, artifact = coordinate.group("group", "artifact")
            if not coordinate_is_approved(group, artifact, policy):
                errors.append(
                    f"unapproved dependency in {relative}: {group}:{artifact}; "
                    "use an allowlisted artifact or update the Weft policy"
                )
        return errors

    alias_match = re.search(r"\blibs\.([A-Za-z0-9_.-]+)", argument)
    if alias_match:
        alias = normalize_catalog_alias(alias_match.group(1))
        coordinate = aliases.get(alias)
        if coordinate is None:
            errors.append(f"unresolved version catalog alias in {relative}: libs.{alias_match.group(1)}")
            return errors
        group, artifact = coordinate
        if not coordinate_is_approved(group, artifact, policy):
            errors.append(
                f"unapproved dependency in {relative}: {group}:{artifact} via libs.{alias_match.group(1)}; "
                "use an allowlisted artifact or update the Weft policy"
            )
        return errors

    errors.append(
        f"unsupported dependency notation in {relative}: {argument.strip()}; "
        "use a Maven coordinate, a local project dependency, kotlin(test), or a default version catalog alias"
    )
    return errors


def verify_project_dependencies(project: Path, profile: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    gradle_files = [*project.rglob("*.gradle.kts"), *project.rglob("*.gradle")]
    if not gradle_files:
        return errors
    try:
        policy = json.loads(DEPENDENCY_POLICY_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [f"cannot load dependency allowlist: {error}"]
    if policy.get("schemaVersion") != 1 or policy.get("mode") != "strict-direct-dependency-allowlist":
        return ["dependency allowlist has an unsupported schema or mode"]

    aliases = load_catalog_coordinates(project)
    server_kinds = set(policy.get("serverModuleKinds", []))
    server_roots = [
        Path(module["path"]).parts
        for module in profile["modules"]
        if module.get("kind") in server_kinds
    ]
    configurations = re.compile(
        r"\b(?:api|implementation|compileOnly|runtimeOnly|ksp|[A-Za-z0-9_]*(?:Api|Implementation|CompileOnly|RuntimeOnly))\s*\("
    )

    for build_file in gradle_files:
        relative = build_file.relative_to(project)
        if any(relative.parts[: len(server_root)] == server_root for server_root in server_roots):
            continue
        source = build_file.read_text(encoding="utf-8")
        is_build_logic = any(part in {"build-logic", "buildSrc"} for part in relative.parts)

        if is_build_logic:
            # Build-logic's own implementation dependencies are not app libraries. Still inspect
            # explicit additions to app configurations so convention plugins cannot hide them.
            for match in ADD_DEPENDENCY_RE.finditer(source):
                arguments = read_call_argument(source, match.end() - 1)
                if arguments is None:
                    errors.append(f"cannot parse build-logic dependency addition in {relative}")
                    continue
                parts = split_top_level_arguments(arguments)
                if len(parts) < 2:
                    continue
                configuration = re.fullmatch(r"['\"]([^'\"]+)['\"]", parts[0])
                if configuration and is_dependency_configuration(configuration.group(1)):
                    errors.extend(verify_dependency_argument(parts[1], relative, aliases, policy))
                elif match.group(1) or COORDINATE_RE.search(parts[1]) or "libs." in parts[1]:
                    errors.append(f"unsupported build-logic dependency configuration in {relative}: {parts[0]}")
            continue

        for match in configurations.finditer(source):
            argument = read_call_argument(source, match.end() - 1)
            if argument is None:
                errors.append(f"cannot parse dependency declaration in {relative}")
                continue
            errors.extend(verify_dependency_argument(argument, relative, aliases, policy))

        for match in GROOVY_DEPENDENCY_RE.finditer(source):
            coordinate = COORDINATE_RE.search(match.group(2))
            if not coordinate:
                errors.append(f"unsupported dependency notation in {relative}: {match.group(2)}")
                continue
            group, artifact = coordinate.group("group", "artifact")
            if not coordinate_is_approved(group, artifact, policy):
                errors.append(
                    f"unapproved dependency in {relative}: {group}:{artifact}; "
                    "use an allowlisted artifact or update the Weft policy"
                )

        for match in ADD_DEPENDENCY_RE.finditer(source):
            arguments = read_call_argument(source, match.end() - 1)
            if arguments is None:
                errors.append(f"cannot parse dependency addition in {relative}")
                continue
            parts = split_top_level_arguments(arguments)
            if len(parts) < 2:
                if match.group(1):
                    errors.append(f"cannot parse dependencies.add(...) declaration in {relative}")
                continue
            configuration = re.fullmatch(r"['\"]([^'\"]+)['\"]", parts[0])
            if configuration:
                if not is_dependency_configuration(configuration.group(1)):
                    continue
            elif match.group(1) or COORDINATE_RE.search(parts[1]) or "libs." in parts[1]:
                errors.append(f"unsupported dependency configuration in {relative}: {parts[0]}")
                continue
            else:
                continue
            errors.extend(verify_dependency_argument(parts[1], relative, aliases, policy))

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
