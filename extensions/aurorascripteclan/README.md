# Aurora Scripted LAN (Phase 6 demo)

Thin Mihon extension: only `aurora.scripted=1` assets. Host runs QuickJS + OkHttp.

## Recommended tablet path (USB reverse — avoids Wi-Fi firewall)

```powershell
# PC terminal 1 — demo server
$env:JAVA_HOME="D:\Android\jbr"
cd D:\Projects\AuroraReader-mihon\scripts
& "$env:JAVA_HOME\bin\javac.exe" Phase6DemoServer.java
& "$env:JAVA_HOME\bin\java.exe" Phase6DemoServer 8765

# PC terminal 2 — reverse + build/install extension
adb -s HA1XJZHC reverse tcp:8765 tcp:8765
$env:JAVA_HOME="D:\Android\jbr"
cd D:\Projects\AuroraExtensions\extensions\aurorascripteclan
.\gradlew.bat :app:assembleDebug -PdemoBaseUrl=http://127.0.0.1:8765
adb -s HA1XJZHC install -r app\build\outputs\apk\debug\app-debug.apk
```

Host Debug APK must include Phase 6 (`feature/scripted-source-phase6`).

## Wi-Fi path

Use `-PdemoBaseUrl=http://<PC-LAN-IP>:8765` and **admin** firewall allow TCP 8765 inbound.
Tablet must reach that IP (same LAN; no AP client isolation).

## Hand-test checklist

1. Browse → Sources → **Aurora Scripted LAN**
2. Popular shows **Aurora Demo**
3. Open manga → details/chapters (**Loop Author**, Chapter 1)
4. Start → Reader pages load (2 images)
5. Download chapter

## Notes

- `EmptySourceFactory` returns no Kotlin `HttpSource`; host loads ScriptedSource from assets only.
- Cover/page images need a real JPEG (`scripts/pixel.jpg` next to the Java server).
