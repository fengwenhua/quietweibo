#!/usr/bin/env python3
"""Verify a signed release APK: certificate pin, package id, version and debug flag.

Used by .github/workflows/release.yml after Gradle assembles the signed release.
Only public values are printed; no keystore material is read.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


def checked(command):
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise SystemExit("command failed: %s\n%s" % (Path(command[0]).name, result.stderr.strip()))
    return result.stdout


def read_properties(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--trust", type=Path, default=Path("release-trust.properties"))
    parser.add_argument("--version", type=Path, default=Path("version.properties"))
    args = parser.parse_args()

    version = read_properties(args.version)
    expected_cert = read_properties(args.trust).get("certificateSha256", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_cert):
        raise SystemExit(
            "release-trust.properties 未配置有效的 certificateSha256；"
            "请先替换成 QuietWeibo 自己的 release 证书 SHA-256")

    report = checked([str(args.build_tools / "apksigner"), "verify", "--print-certs", str(args.apk)])
    certs = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", report)
    if len(certs) != 1:
        raise SystemExit("expected exactly one signer certificate")
    cert = certs[0].lower()
    if cert != expected_cert:
        raise SystemExit("certificate mismatch: %s != %s" % (cert, expected_cert))

    badging = checked([str(args.build_tools / "aapt2"), "dump", "badging", str(args.apk)])
    package = "io.github.fengwenhua.quietweibo"
    for needle in (
            "package: name='%s'" % package,
            "versionCode='%s'" % version["versionCode"],
            "versionName='%s'" % version["versionName"]):
        if needle not in badging:
            raise SystemExit("APK badging mismatch: missing %r" % needle)
    if "application-debuggable" in badging:
        raise SystemExit("release APK must not be debuggable")

    print(json.dumps({
        "apk": args.apk.name,
        "package": package,
        "versionCode": int(version["versionCode"]),
        "versionName": version["versionName"],
        "certificateSha256": cert,
    }, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
