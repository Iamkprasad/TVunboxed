# TVUNBOXED_ARCHITECTURE

```
TVunboxed platform
├── Python bridge (existing, reference)
│   ├── tvunboxed/protocol.py      wire records
│   ├── discovery.py / client.py   UDP + TCP session
│   ├── tvapps.py                  app-list binary parser
│   ├── tvdav.py / share.py        WebDAV + share
│   ├── tvcare.py / care_cli.py    guarded maintenance
│   └── adb.py                     optional, blocked on this TV
└── Android app (new, com.tvunboxed.app)
    ├── core/
    │   ├── Protocol.kt            command builders (pure, unit-tested)
    │   ├── Discovery.kt           48689 broadcast + reply parser
    │   ├── VendorSession.kt       8121 socket, reconnect, app-list fetch
    │   ├── AppListParser.kt       little-endian record parser
    │   ├── WebDavClient.kt        OkHttp PROPFIND/PUT/GET/DELETE/COPY/MOVE
    │   ├── MediaServer.kt         NanoHTTPD + Range, phone→TV URLs
    │   ├── Capabilities.kt        status registry
    │   ├── CapabilityProbe.kt     evidence-based detection
    │   └── DeviceStore.kt         SharedPreferences persistence
    ├── MainViewModel.kt           state, coroutines, command routing
    ├── MainActivity.kt            navigation, Share Sheet intake
    └── ui/                        five screens + shared components
```

## Communication flows

**Connect**: scan (UDP broadcast) or manual IP → `VendorSession.connect()` sends `sayHello` + `auth` → `CapabilityProbe` checks control/WebDAV/screencapture → statuses stored per device.

**Remote**: Compose button → `MainViewModel.key/volume/...` → `Protocol.*` → `VendorSession.send()` on IO dispatcher → 15s heartbeat coroutine keeps the socket warm.

**Cast**: phone file → copied into app cache → `MediaServer.add()` returns `http://<phone-ip>:8888/media/<id>` → `OpenLink` sent to TV → TV's own player fetches with Range support. `Savefile`/WebDAV PUT is the COPY path.

**TV view**: `GET /remote/screencapture` → bytes → bitmap → tap coordinates normalized 0..1000 and scaled by `GETFBINFO` reply → `TOUCHEVENT`/`MIRRORTOUCHEVENT`.

**Files**: WebDavClient PROPFIND listing → upload/download/delete with confirmation → browse path state.

**Care**: diagnostics collects only WebDAV-visible facts plus capability statuses; CPU/RAM reported as `Unavailable`.

## Extension points
- New control protocol = new adapter implementing the same session interface; UI depends on ViewModel state, not the protocol adapter classes.
- Optional receiver APK: WebRTC/HTTP receiver with foreground service, explicit session; not required for v1.
- Rust core can replace `core/*` later via JNI/UniFFI without changing Compose UI.
