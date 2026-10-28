#!/usr/bin/env python3
"""Prepare verified, standalone APKs and checksums for a versioned GitHub release."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from apk_certificate import certificate_digest


def prepare(tag, output, apksigner, expected_certificate=None, variant="debug"):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.\d+)?", tag):
        raise ValueError("Release tag must be a version, optionally with alpha/beta/rc.N")
    source = Path("app/build/outputs/apk") / variant
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
        verification = subprocess.run([apksigner, "verify", "--print-certs", str(path)], check=True, capture_output=True, text=True)
        if expected_certificate is not None:
            expected_digest = expected_certificate.read_text().strip().lower()
            if certificate_digest(verification.stdout) != expected_digest:
                raise ValueError("APK certificate does not match the pinned stable signing certificate")
        if variant == "release":
            aapt = str(Path(apksigner).with_name("aapt2"))
            badging = subprocess.run([aapt, "dump", "badging", str(path)], check=True, capture_output=True, text=True).stdout
            package = badging.splitlines()[0]
            if "name='nl.veyra.browser'" not in package or f"versionName='{tag[1:]}'" not in package or f"versionCode='{entry['versionCode']}'" not in package:
                raise ValueError("Actual APK package/version does not match metadata")
            if "application-debuggable" in badging or "application-testOnly" in badging:
                raise ValueError("Public release APK must not be debuggable or test-only")
            if f"native-code: '{abi}'" not in badging:
                raise ValueError("Actual APK native ABI does not match metadata")
            subprocess.run([str(Path(apksigner).with_name("zipalign")), "-c", "-P", "16", "4", str(path)], check=True, capture_output=True)
        seen.add(abi)
    if seen != expected:
        raise ValueError("All supported ABIs must be present")
    output.mkdir(parents=True, exist_ok=True)
    checksums = []
    for entry in entries:
        abi = entry["filters"][0]["value"]
        name = f"Veyra-{tag[1:]}-{abi}" + ("-debug" if variant == "debug" else "") + ".apk"
        destination = output / name
        shutil.copy2(source / entry["outputFile"], destination)
        with destination.open("rb") as uploaded:
            digest = hashlib.file_digest(uploaded, "sha256").hexdigest()
        checksums.append(f"{digest}  {name}")
        print(f"Verified {name}", flush=True)
    (output / "SHA256SUMS.txt").write_text("\n".join(sorted(checksums)) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--expected-certificate", type=Path)
    parser.add_argument("--variant", choices=("debug", "release"), default="debug")
    args = parser.parse_args()
    prepare(args.tag, args.output, args.apksigner, args.expected_certificate, args.variant)
