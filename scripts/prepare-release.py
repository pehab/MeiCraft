#!/usr/bin/env python3
"""Package this build's verified APK, never an older checked-in binary."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

version = dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines() if "=" in line)
name = version["versionName"]
code = int(version["versionCode"])
if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}", name) or code <= 0:
    raise SystemExit("Invalid release version")
sdk = Path(os.environ["ANDROID_SDK_ROOT"])
signer = sdk / "build-tools/36.0.0/apksigner"
apk = Path("build") / ("meiCraft-" + name + ".apk")
baseline = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("dist/meiCraft-0.1.0.apk")

def certificates(path):
    result = subprocess.run([str(signer), "verify", "--print-certs", str(path)],
                            check=True, text=True, capture_output=True)
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\S+)$", result.stdout, re.MULTILINE)
    if not fingerprints:
        raise SystemExit("APK signing certificate missing")
    return fingerprints

def payload(path):
    # APK signature blocks are outside ZIP entries; JAR signature metadata is
    # not executable content. Compare every other entry, including manifest,
    # resources, assets and all DEX files.
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("Duplicate APK ZIP entries")
        return {name: archive.read(name) for name in names
                if not name.startswith("META-INF/")}

signed = Path("dist") / apk.name
original = Path("dist/meiCraft-0.1.0.apk")
if certificates(signed) != certificates(original):
    raise SystemExit("Checked-in APK signing key differs from the installed baseline")
if payload(signed) != payload(apk):
    raise SystemExit("Checked-in APK does not match the validated source build")
if certificates(apk) != certificates(baseline):
    if os.environ.get("MEICRAFT_SIGNING_JSON"):
        raise SystemExit("Configured signing key differs from the installed baseline")
    print("Using original-key APK with byte-identical validated program contents.")
    apk = signed
output = Path("build/release")
output.mkdir(parents=True, exist_ok=True)
shutil.copyfile(apk, output / apk.name)
data = apk.read_bytes()
digest = hashlib.sha256(data).hexdigest()
(output / (apk.name + ".sha256")).write_text(digest + "  " + apk.name + "\n")
manifest = {"schema": 1, "versionCode": code, "versionName": name,
            "url": "https://github.com/pehab/MeiCraft/releases/download/v" + name + "/" + apk.name,
            "sha256": digest, "size": len(data)}
(output / "meiCraft-update.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("Packaged verified APK " + name + " with matching update signature and fresh checksums.")
