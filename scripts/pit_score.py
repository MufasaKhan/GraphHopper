#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def get_score(root: Path) -> float:
    # Find the most recent mutations.xml under this root
    candidates = sorted(
        root.glob("**/target/pit-reports/*/mutations.xml"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not candidates:
        return 0.0

    path = candidates[0]
    try:
        tree = ET.parse(path)
    except Exception:
        return 0.0

    root_el = tree.getroot()
    mutations = root_el.findall(".//mutation")
    total = len(mutations)
    killed = sum(
        1 for m in mutations
        if (m.get("status", "") or "").upper() == "KILLED"
    )

    if total == 0:
        return 100.0
    return 100.0 * killed / total

if __name__ == "__main__":
    base = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    score = get_score(base)
    print(f"{score:.4f}")
