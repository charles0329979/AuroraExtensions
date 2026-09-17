import re
import sys

xml = sys.stdin.read()
for m in re.finditer(
    r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    xml,
):
    t = m.group(1)
    if "Chapter" in t or "章节" in t or t.startswith("第"):
        print(f"{t} @ ({m.group(2)},{m.group(3)})-({m.group(4)},{m.group(5)})")
