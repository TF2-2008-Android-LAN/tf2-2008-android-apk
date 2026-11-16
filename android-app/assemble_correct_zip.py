#!/usr/bin/env python3
"""Assemble an Android 11+ compatible APK with stored resources.arsc."""
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

project = Path(sys.argv[1])
build = project / "build"
manifest_only = build / "manifest_only.apk"
unaligned = build / "unaligned.apk"
if not manifest_only.exists():
    raise RuntimeError("Missing aapt2 output")
paths = [
    (build / "dex" / "classes.dex", "classes.dex"),
]
paths += [(asset, f"assets/{asset.name}") for asset in sorted((project/"assets").iterdir()) if asset.is_file()]
for path, _ in paths:
    if not path.is_file():
        raise RuntimeError(f"Missing pack input: {path}")

names = set()
with ZipFile(manifest_only, "r") as source, ZipFile(unaligned, "w", allowZip64=True) as target:
    for item in source.infolist():
        name = item.filename
        if name in names:
            raise RuntimeError("Duplicate source APK entry: " + name)
        names.add(name)
        if item.is_dir():
            target.writestr(item, b"", compress_type=ZIP_STORED)
        else:
            stored = (name == "resources.arsc" or name.endswith(".so"))
            target.writestr(item, source.read(item), compress_type=ZIP_STORED if stored else ZIP_DEFLATED)
    for filepath, name in paths:
        if name in names:
            raise RuntimeError("Duplicate added entry: " + name)
        names.add(name)
        stored = name.endswith(".apk") or name.endswith(".so")
        target.write(filepath, name, compress_type=ZIP_STORED if stored else ZIP_DEFLATED)

with ZipFile(unaligned, "r") as apk:
    entry = apk.getinfo("resources.arsc")
    if entry.compress_type != ZIP_STORED:
        raise RuntimeError("resources.arsc MUST be stored without compression")
    if apk.getinfo("assets/Source_Engine_1.17.25.apk").file_size != 79491233:
        raise RuntimeError("Embedded Source APK size mismatch")
    if apk.getinfo("assets/TF2_2008_Game_1.00.apk").file_size != 16845348:
        raise RuntimeError("Embedded game APK size mismatch")
    print("ZIP_ASSEMBLY_OK", len(names), "entries", "resources.arsc ZIP_STORED", 
          "size", unaligned.stat().st_size)
