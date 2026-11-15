#!/bin/sh
# TVunboxed quick start: discovers the TV and serves the remote on :8080.
# Extra args are passed through, e.g. ./start.sh --http-port 9000
cd "$(dirname "$0")" && exec python3 -m tvunboxed "$@"
