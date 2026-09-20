import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else r"D:\Projects\AuroraExtensions\ui-reader.xml"
xml = open(path, encoding="utf-8", errors="ignore").read()
texts = [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]
print("TEXTS:", texts[:50])
for key in ("1/", "页", "错误", "Error", "重试", "Retry", "Chapter"):
    if any(key in t for t in texts):
        print("hit:", key)
