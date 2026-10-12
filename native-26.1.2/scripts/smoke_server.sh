#!/usr/bin/env bash
# Phase 1 CI-only dedicated server smoke test. Run solely in an isolated runner.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p run/config build/reports/gaslight
# EULA acceptance is applied ONLY inside the disposable CI working directory.
printf 'eula=true\n' > run/eula.txt
printf 'enable=false\nallowRemoteChunkGeneration=false\n' > run/config/gaslight-native.properties
if [[ ! -e run/server.properties ]]; then
  printf 'server-ip=127.0.0.1\nserver-port=0\n' > run/server.properties
fi
log="$PWD/build/reports/gaslight/server-smoke.log"
echo 'Starting disposable Fabric dedicated-server smoke test (auto-mod disabled).'
# The server stays running after startup. Its first successful boot is the test.
timeout --signal=TERM --kill-after=10s 180s gradle --no-daemon runServer --console=plain >"$log" 2>&1 &
pid=$!
cleanup(){
  if kill -0 "$pid" 2>/dev/null; then kill -TERM "$pid" 2>/dev/null || true; fi
  wait "$pid" 2>/dev/null || true
}
trap cleanup EXIT
ready=0
for ((i=0;i<175;i++)); do
  # Require both the GASLIGHT Fabric entrypoint and Minecraft's ready message.
  if grep -Fq '[GASLIGHT native] registered standalone traversal;' "$log" && \
     grep -Eq 'Done \([0-9.,]+s\)!|Done \([0-9.,]+s\)! For help' "$log"; then
    ready=1
    break
  fi
  if ! kill -0 "$pid" 2>/dev/null; then break; fi
  sleep 1
done
if ((ready==0)); then
  echo 'FAIL: no verified Minecraft+GASLIGHT startup; last log lines:' >&2
  tail -100 "$log" >&2 || true
  exit 1
fi
if grep -Eq 'Exception in thread|Failed to start the minecraft server|EntrypointException|ModResolutionException' "$log"; then
  echo 'FAIL: critical launch exception detected' >&2
  tail -100 "$log" >&2
  exit 1
fi
echo 'PASS: Minecraft dedicated server reached READY with GASLIGHT Fabric entrypoint loaded.'
