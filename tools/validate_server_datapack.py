"""Validate the starter pack, offline schema, guide examples, and optional release ZIP.

Test dependency: python -m pip install jsonschema==4.22.0
Run from any directory: python tools/validate_server_datapack.py [--zip <archive>]
"""

import argparse
import copy
import json
from pathlib import Path
import re
import zipfile

from jsonschema import Draft7Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "datapacks/server-earth"
PRESET = Path("data/terrarium_expanded/worldgen/world_preset/earth.json")


def biome_source(preset):
    return preset["dimensions"]["minecraft:overworld"]["generator"]["biome_source"]


def validate(archive=None):
    preset = json.loads((PACK / PRESET).read_text(encoding="utf-8"))
    schema_path = (PACK / PRESET.parent / preset["$schema"]).resolve()
    assert schema_path.is_relative_to(PACK.resolve()), "Schema must be bundled inside the pack"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    Draft7Validator.check_schema(schema)
    validator = Draft7Validator(schema, format_checker=FormatChecker())
    validator.validate(preset)
    builtin = json.loads((ROOT / "common/src/main/resources" / PRESET).read_text())
    assert preset["dimensions"] == builtin["dimensions"], "Starter defaults drifted from the built-in preset"

    guide = (ROOT / "documentation/SERVER_SETUP.md").read_text(encoding="utf-8")
    for fragment in re.findall(r"```json\s*\n(.*?)\n```", guide, re.DOTALL):
        example = copy.deepcopy(preset)
        biome_source(example).update(json.loads("{" + fragment + "}"))
        validator.validate(example)

    # Catch likely admin mistakes, including typos that Minecraft's codecs may ignore.
    for field, value in [("zoom", 7), ("zoom", 13), ("zoom", "10"), ("spawn_latitude", 90),
                         ("biome_integration", "naturespirit"), ("world_boder", True),
                         ("generation", {"cave": True}), ("inland_water", {"min_water_months": 13}),
                         ("inland_water", {"min_water_months": 1.5})]:
        invalid = copy.deepcopy(preset)
        biome_source(invalid)[field] = value
        assert not validator.is_valid(invalid), f"Schema accepted invalid {field}={value!r}"

    if archive:
        with zipfile.ZipFile(archive) as pack_zip:
            assert "pack.mcmeta" in pack_zip.namelist(), "Missing pack.mcmeta at ZIP root"
            expected_files = {path.relative_to(PACK).as_posix() for path in PACK.rglob("*") if path.is_file()}
            actual_files = {name for name in pack_zip.namelist() if not name.endswith("/")}
            assert actual_files == expected_files, "ZIP contains missing or unexpected files"
            presets = {name for name in actual_files if "/worldgen/world_preset/" in name and name.endswith(".json")}
            assert presets == {PRESET.as_posix()}, "Starter must override the bundled Earth preset without adding another level type"
            for path in PACK.rglob("*"):
                if path.is_file():
                    name = path.relative_to(PACK).as_posix()
                    assert pack_zip.read(name) == path.read_bytes(), f"ZIP mismatch: {name}"
            validator.validate(json.loads(pack_zip.read(PRESET.as_posix())))
    print("Server datapack: schema, preset, guide examples, invalid-value cases, and requested ZIP checks passed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, help="Also validate a packaged release ZIP")
    validate(parser.parse_args().zip)
