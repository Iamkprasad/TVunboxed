# Methodology & provenance

This repository contains no code copied or mechanically translated from any vendor. It is a from-scratch, black-box implementation.

## How knowledge was obtained

1. **Static study of the stock remote app** (decompiled for interoperability only — to learn what the wire protocol is, not to reproduce program logic). No vendor code, strings, assets or binaries are included or redistributed here.
2. **Live observation of the TV** (owner's device, owner's consent): UDP broadcast probe for the control port; TCP handshake; features round-trip; a keypress to confirm the command channel.
3. **Independent re-derivation**: the packet layout, message formats and command inventory were rewritten by the author after the observations were recorded, using new code and new wording.

## What is and isn't protected

- Command verbs and port numbers visible on the wire are **facts about the protocol**, not expressive source code. Facts cannot be copyrighted, and interoperability requires using them; they are reproduced here for that reason only.
- Everything else — architecture, names, data structures, HTTP bridge, UI, tests, documentation — is original to this project.
- The handshake marker embedded in the stock client is required to complete the handshake its server expects. It is a fixed constant in that client; it confers no privileges beyond what the unauthenticated server grants any LAN client anyway.

## What this project does not claim

- Not affiliated with the TV vendor or the stock remote project.
- Not a clean-room implementation in the strict legal sense (one author did both the study and the implementation). If you need that standard (e.g., for commercial distribution), commission a genuine clean-room re-implementation.
- No warranty; see LICENSE.
