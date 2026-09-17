import re,sys
raw=open(sys.argv[1],encoding="utf-8",errors="replace").read()
for m in re.finditer(r"<node[^>]+>", raw):
    n=m.group(0)
    t=re.search(r'text="([^"]*)"', n)
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    d=re.search(r'content-desc="([^"]*)"', n)
    if t and t.group(1) and b:
        print("%s [%s,%s]-[%s,%s]" % (t.group(1), b.group(1), b.group(2), b.group(3), b.group(4)))
    elif d and d.group(1) and b:
        print("desc=%s [%s,%s]-[%s,%s]" % (d.group(1), b.group(1), b.group(2), b.group(3), b.group(4)))