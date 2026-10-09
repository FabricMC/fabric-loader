#!/usr/bin/env python3
"""Fetch the client classes used by the menu compatibility check."""

import argparse
import concurrent.futures
import io
import json
from pathlib import Path
import struct
import os
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import quote
import zipfile


def fetch(url, headers=None):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=45) as response:
                return response.read(), response.headers
        except (OSError, urllib.error.URLError) as error:
            if isinstance(error, urllib.error.HTTPError) and 400 <= error.code < 500 and error.code != 429:
                raise
            if attempt == 3:
                raise
            time.sleep(attempt + 1)


def read_json(url):
    return json.loads(fetch(url)[0])


def save(path, data):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as temporary:
        temporary.write(data)
        name = temporary.name
    os.replace(name, path)


class RemoteJar(io.RawIOBase):
    def __init__(self, url, size):
        self.url = url
        self.size = size
        self.position = 0
        self.blocks = {}
        self.full_data = None

    def seekable(self):
        return True

    def seek(self, offset, whence=0):
        self.position = offset + (0 if whence == 0 else self.position if whence == 1 else self.size)
        return self.position

    def tell(self):
        return self.position

    def read(self, size=-1):
        end = min(self.size, self.position + size if size >= 0 else self.size)
        if end - self.position >= 65536 and self.full_data is None:
            start = self.position
            data, headers = fetch(self.url + f"?menu-range={start}-{end - 1}", {"Range": f"bytes={start}-{end - 1}"})
            if headers.get("Content-Range") == f"bytes {start}-{end - 1}/{self.size}" and len(data) == end - start:
                self.position = end
                return data
            if headers.get("Content-Range") is None and len(data) == self.size:
                self.full_data = data
            else:
                raise IOError(f"Invalid range response for {self.url}: {headers.get('Content-Range')}")
        result = bytearray()
        while self.position < end:
            block = self.position // 262144
            if block not in self.blocks:
                start = block * 262144
                last = min(self.size, start + 262144) - 1
                if self.full_data is not None:
                    self.blocks[block] = self.full_data[start:last + 1]
                    continue
                data, headers = fetch(self.url + f"?menu-block={block}", {"Range": f"bytes={start}-{last}"})
                expected = f"bytes {start}-{last}/{self.size}"
                if headers.get("Content-Range") is None and len(data) == self.size:
                    self.full_data = data
                if self.full_data is not None:
                    self.blocks[block] = self.full_data[start:last + 1]
                    continue
                if headers.get("Content-Range") != expected or len(data) != last - start + 1:
                    raise IOError(f"Invalid range response for {self.url}: {headers.get('Content-Range')}")
                self.blocks[block] = data
            offset = self.position % 262144
            part = self.blocks[block][offset:offset + end - self.position]
            result.extend(part)
            self.position += len(part)
        return bytes(result)


TARGETS = {
    "net/minecraft/class_437", "net/minecraft/class_442", "net/minecraft/class_4185",
    "net/minecraft/class_2561", "net/minecraft/class_2585", "net/minecraft/class_310",
    "net/minecraft/class_339", "net/minecraft/class_364", "net/minecraft/class_4068", "net/minecraft/class_4264",
    "net/minecraft/class_342", "net/minecraft/class_332", "net/minecraft/class_327",
}
NAMED = {
    "net/minecraft/client/gui/screens/Screen", "net/minecraft/client/gui/screens/TitleScreen",
    "net/minecraft/client/gui/components/Button", "net/minecraft/network/chat/Component",
    "net/minecraft/client/Minecraft", "net/minecraft/client/gui/components/AbstractWidget",
    "net/minecraft/client/gui/components/events/GuiEventListener",
    "net/minecraft/client/gui/components/AbstractButton",
    "net/minecraft/client/gui/components/EditBox", "net/minecraft/client/gui/GuiGraphics",
    "net/minecraft/client/gui/GuiGraphicsExtractor", "net/minecraft/client/gui/Font",
    "net/minecraft/client/input/MouseButtonEvent", "net/minecraft/client/input/MouseButtonInfo",
}


def parents(data):
    count = struct.unpack_from(">H", data, 8)[0]
    pool = [None] * count
    position = 10
    index = 1
    while index < count:
        tag = data[position]
        position += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, position)[0]
            position += 2
            pool[index] = data[position:position + length].decode("utf-8", errors="replace")
            position += length
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = struct.unpack_from(">H", data, position)[0]
            position += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            position += 4
        elif tag in (5, 6):
            position += 8
            index += 1
        elif tag == 15:
            position += 3
        else:
            raise ValueError(f"Unknown constant pool tag {tag}")
        index += 1
    superclass, count = struct.unpack_from(">HH", data, position + 4)
    indices = [superclass] + list(struct.unpack_from(">" + "H" * count, data, position + 8))
    return [pool[pool[i]] for i in indices if i]


def download(game, manifest, intermediaries, output):
    version = game["version"]
    folder = output / version
    cached = json.loads((folder / "complete.json").read_text()) if (folder / "complete.json").exists() else None
    if cached and cached.get("schema") == 3:
        return version, "cached"
    if version not in manifest:
        return version, "missing Mojang manifest"
    metadata = {"downloads": {"client": cached["client"]}} if cached else read_json(manifest[version]["url"])
    folder.mkdir(parents=True, exist_ok=True)
    names = set(NAMED)
    if version in intermediaries:
        mapping_file = folder / "mappings.tiny"
        if mapping_file.exists():
            mappings = mapping_file.read_bytes()
        else:
            artifact_version = quote(version, safe="")
            base = f"https://maven.fabricmc.net/net/fabricmc/intermediary/{artifact_version}/intermediary-{artifact_version}"
            try:
                jar, _ = fetch(base + "-v2.jar")
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
                jar, _ = fetch(base + ".jar")
            with zipfile.ZipFile(io.BytesIO(jar)) as archive:
                mappings = archive.read("mappings/mappings.tiny")
        (folder / "mappings.tiny").write_bytes(mappings)
        for line in mappings.decode().splitlines():
            columns = line.split("\t")
            if columns[0] in ("c", "CLASS") and columns[-1] in TARGETS:
                names.add(columns[1])
    client = metadata["downloads"]["client"]
    with zipfile.ZipFile(RemoteJar(client["url"], client["size"])) as archive:
        entries = set(archive.namelist())
        pending = [entry for entry in entries if any(entry == name + ".class" or entry.startswith(name + "$") for name in names)]
        visited = set()
        while pending:
            entry = pending.pop()
            if entry in visited or entry not in entries:
                continue
            visited.add(entry)
            destination = folder / "classes" / entry
            data = destination.read_bytes() if destination.exists() else b""
            if not data.startswith(b"\xca\xfe\xba\xbe"):
                data = archive.read(entry)
            destination.parent.mkdir(parents=True, exist_ok=True)
            save(destination, data)
            pending.extend(parent + ".class" for parent in parents(data))
    (folder / "complete.json").write_text(json.dumps({"schema": 3, "version": version, "stable": game["stable"], "client": client}))
    return version, "downloaded"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("--versions", nargs="*")
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    games = read_json("https://meta.fabricmc.net/v2/versions/game")
    manifest = {v["id"]: v for v in read_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")["versions"]}
    experimental = read_json("https://maven.fabricmc.net/net/minecraft/experimental_versions.json")["versions"]
    manifest.update({v["id"]: v for v in experimental})
    intermediaries = {v["version"] for v in read_json("https://meta.fabricmc.net/v2/versions/intermediary")}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "catalog.json").write_text(json.dumps(games, indent=2))
    selected = [g for g in games if not args.versions or g["version"] in args.versions]
    failures = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        jobs = {pool.submit(download, g, manifest, intermediaries, args.output): g["version"] for g in selected}
        for job in concurrent.futures.as_completed(jobs):
            try:
                version, status = job.result()
                print(version, status, flush=True)
                if status not in ("cached", "downloaded"):
                    failures.append({"version": version, "error": status})
            except Exception as error:
                failures.append({"version": jobs[job], "error": str(error)})
                print(jobs[job], str(error), flush=True)
    (args.output / "download-failures.json").write_text(json.dumps(failures, indent=2))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
