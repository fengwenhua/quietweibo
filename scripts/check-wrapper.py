#!/usr/bin/env python3
"""Verify the reviewed Gradle Wrapper bootstrap before executing it in CI."""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR_SHA256 = "498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
ZIP_SHA256 = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"


def main():
    wrapper = ROOT / "gradle/wrapper"
    if hashlib.sha256((wrapper / "gradle-wrapper.jar").read_bytes()).hexdigest() != JAR_SHA256:
        raise SystemExit("Unexpected Gradle Wrapper JAR")
    properties = dict(
        line.split("=", 1)
        for line in (wrapper / "gradle-wrapper.properties").read_text().splitlines()
        if "=" in line
    )
    if properties.get("distributionSha256Sum") != ZIP_SHA256:
        raise SystemExit("Unexpected Gradle distribution checksum")
    if properties.get("distributionUrl") != r"https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip":
        raise SystemExit("Unexpected Gradle distribution URL")
    print("Gradle Wrapper JAR and distribution pins verified")


if __name__ == "__main__":
    main()
