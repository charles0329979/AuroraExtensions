import re
import sys

xml = sys.stdin.read()
texts = [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]
for t in texts[:80]:
    print(t)
print("---MATCHES---")
for m in re.finditer(
    r'text="([^"]*(?:Aurora|Scripted|Stub|MangaDex|信任|扩展|插件|Sources|Extensions)[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    xml,
    re.I,
):
    print(f"{m.group(1)} @ ({m.group(2)},{m.group(3)})-({m.group(4)},{m.group(5)})")
