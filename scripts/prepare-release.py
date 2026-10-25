#!/usr/bin/env python3
"""Package this build's verified APK, never an older checked-in binary."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

version = dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines() if "=" in line)
name = version["versionName"]
code = int(version["versionCode"])
if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}", name) or code <= 0:
    raise SystemExit("Invalid release version")
sdk = Path(os.environ["ANDROID_SDK_ROOT"])
signer = sdk / "build-tools/36.0.0/apksigner"
apk = Path("build") / ("meiCraft-" + name + ".apk")
baseline = Path("dist/meiCraft-0.1.0.apk")

def certificates(path):
    result = subprocess.run([str(signer), "verify", "--print-certs", str(path)],
                            check=True, text=True, capture_output=True)
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\S+)$", result.stdout, re.MULTILINE)
    if not fingerprints:
        raise SystemExit("APK signing certificate missing")
    return fingerprints

def payload(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("Duplicate APK archive entries")
        return {name: archive.read(name) for name in names
                if not re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", name, re.IGNORECASE)}

original = certificates(baseline)
if certificates(apk) != original:
    # CI can validate an offline-signed build without access to the private key.
    signed = Path("dist") / apk.name
    if not signed.is_file() or certificates(signed) != original:
        raise SystemExit("Original release signature unavailable; refusing an incompatible update")
    if payload(apk) != payload(signed):
        raise SystemExit("Offline-signed APK differs from this source build; rebuild and sign it before release")
    apk = signed
    print("Offline-signed APK exactly matches the fresh build payload.")
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
