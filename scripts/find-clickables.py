import re, sys
xml = sys.stdin.read()
print("---Chapter nodes---")
for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t = m.group(1)
    if "Chapter" in t or "章节" in t or "第" in t:
        print(t, m.group(2), m.group(3), m.group(4), m.group(5))
print("---clickable with text---")
for m in re.finditer(r'clickable="true"[^>]*text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    print(repr(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5))
for m in re.finditer(r'text="([^"]+)"[^>]*clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    print(repr(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5))
