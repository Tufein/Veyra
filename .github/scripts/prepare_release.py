#!/usr/bin/env python3
"""Prepare verified, standalone APKs and checksums for a versioned GitHub release."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def prepare(tag, output, apksigner):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError("Release tag must have the form v0.3.0")
    source = Path("app/build/outputs/apk/debug")
    metadata = json.loads((source / "output-metadata.json").read_text())
    if metadata["applicationId"] != "nl.veyra.browser":
        raise ValueError("Unexpected APK application ID")
    expected = {"arm64-v8a", "armeabi-v7a", "x86_64"}
    entries = metadata["elements"]
    seen = set()
    for entry in entries:
        if entry["versionName"] != tag[1:]:
            raise ValueError("APK version does not match the release tag")
        filters = entry["filters"]
        if len(filters) != 1 or filters[0]["filterType"] != "ABI":
            raise ValueError("Expected one standalone APK per ABI")
        abi = filters[0]["value"]
        if abi not in expected or abi in seen:
            raise ValueError("Unexpected or duplicate APK ABI")
        path = source / entry["outputFile"]
        if path.resolve().parent != source.resolve() or not path.is_file():
            raise ValueError("Missing or invalid APK path")
        subprocess.run([apksigner, "verify", str(path)], check=True)
        seen.add(abi)
    if seen != expected:
        raise ValueError("All supported ABIs must be present")
    output.mkdir(parents=True, exist_ok=True)
    checksums = []
    for entry in entries:
        abi = entry["filters"][0]["value"]
        name = f"Veyra-{tag[1:]}-{abi}-debug.apk"
        destination = output / name
        shutil.copy2(source / entry["outputFile"], destination)
        digest = hashlib.file_digest(destination.open("rb"), "sha256").hexdigest()
        checksums.append(f"{digest}  {name}")
        print(f"Verified {name}", flush=True)
    (output / "SHA256SUMS.txt").write_text("\n".join(sorted(checksums)) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apksigner", required=True)
    args = parser.parse_args()
    prepare(args.tag, args.output, args.apksigner)
