#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def find_mutation_files(root: Path) -> list[Path]:
    """
    Find all mutations.xml files under any pit-reports directory.

    Supports:
      - core/target/pit-reports/mutations.xml
      - core/target/pit-reports/<timestamp>/mutations.xml
      - any similar layout in other modules.
    """
    patterns = [
        "**/target/pit-reports/mutations.xml",
        "**/target/pit-reports/*/mutations.xml",
    ]
    files: list[Path] = []
    seen: set[Path] = set()

    for pattern in patterns:
        for p in root.glob(pattern):
            if p.is_file() and p not in seen:
                seen.add(p)
                files.append(p)

    return files


def get_score(root: Path) -> float:
    files = find_mutation_files(root)
    if not files:
        # No reports -> treat as 0 so CI doesn't silently pass on missing data
        return 0.0

    total = 0
    killed = 0

    for path in files:
        try:
            tree = ET.parse(path)
        except Exception:
            # Broken/partial file -> ignore it
            continue

        root_el = tree.getroot()
        # PIT uses <mutations><mutation .../></mutations>
        muts = root_el.findall(".//mutation")
        total += len(muts)
        killed += sum(
            1
            for m in muts
            if (m.get("status", "") or "").upper() == "KILLED"
        )

    if total == 0:
        # No mutants at all -> 100% (no penalty)
        return 100.0

    return 100.0 * killed / total


if __name__ == "__main__":
    base = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    score = get_score(base)
    # Only print the numeric score; CI depends on this
    print(f"{score:.4f}")
