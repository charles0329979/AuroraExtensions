import re
raw=open(r"D:\Projects\AuroraExtensions\ui-verify.xml",encoding="utf-8",errors="replace").read()
for m in re.finditer(r'text="([^"]*)"', raw):
    t=m.group(1)
    if t and ("rise" in t.lower() or "honja" in t.lower() or "Chapter" in t or "Ch." in t):
        print(repr(t))
print("---search field candidates---")
for m in re.finditer(r'class="android.widget.EditText"[^>]*text="([^"]*)"|text="([^"]*)"[^>]*class="android.widget.EditText"', raw):
    print(m.groups())