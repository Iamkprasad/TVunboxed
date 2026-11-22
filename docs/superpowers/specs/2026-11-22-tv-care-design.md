# TV Care Design

Date: 2026-11-22

## Objective
Give the TV owner a guarded way to audit and reclaim user storage on the target Android TV without root, ADB authorization bypass, firmware writes, or system-package removal.

## Scope
- In scope: `/sdcard` audit, duplicate/large-file suggestions, two-step cleanup with on-TV backup, APK staging to `/sdcard/Upload/`, app inventory read via the control port.
- Out of scope: `/system`, `/data`, `/adb_keys`, boot/recovery, ADB privilege escalation, uninstalling system apps, automatic destructive actions.

## Safety rules
- No firmware flashing, no bootloader unlock attempts, no system-package removal.
- The WebDAV service exposes the entire filesystem root, including `/system` and `/data`. The Care module must never traverse those paths; only `/sdcard` is in scope.
- No port-forwarding of 8000/8121/5555. Bridge stays on a trusted LAN.
- Every destructive operation requires an explicit confirmation token and produces a recoverable backup first.

## Reality check recorded during reconnaissance
- Control-port app list (`GETAPPINFOSEVNET`): 18 apps — mostly vendor/system (Launcher, TV Assistant, vendor share service, TV Store, Miracast, App List, Settings x2).
- `/apkmanager/getinstallapks` returns a shorter curated list (3 apps).
- WebDAV walk of `/sdcard`: ~62 MB of user files; largest are staged APKs (38 MB `MovieBoxTV`, ~16 MB `Vistaar` copies), screenshots, and thumbnails.
- The ~4 GB device storage is dominated by system/vendor partitions and installed packages, which safe channels cannot reclaim. The module reports this honestly rather than promising impossible gains.

## Architecture
- `tvunboxed/tvcare.py` — pure audit/cleanup logic and token checks.
- `tvunboxed/care_cli.py` — CLI entry point (`python3 -m tvunboxed care ...`).
- `tvunboxed/server.py` — HTTP endpoints for the browser UI, reusing the same guards.
- `web/index.html` — Care tab with audit, dry-clean, purge, and stage controls.

## Cleanup flow
1. Audit produces a list of reclaimable files under `/sdcard`.
2. Dry-clean shows what would happen without writing.
3. Cleanup copies selected files to `/sdcard/backups/care/` then deletes originals.
4. Purge requires a second, distinct confirmation token and only accepts backup paths.

## Verification
Unit tests cover classify, path validation, token stability, backup-then-delete order, purge restrictions, and APK staging rejection of non-APK files.
