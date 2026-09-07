#!/bin/sh
# Compile the server and optionally run one of its main classes.
#   tools/build-run.sh                       -> compile only
#   tools/build-run.sh <mainClass> [args...] -> compile, then run
set -e
ROOT=/home/user/evergielinor/ElvargServer
cd "$ROOT"
./gradlew :game:compileJava --console=plain -q 2>&1 | grep -v '^Picked up' | grep -iE '^e:|error:' && exit 1
if [ -n "$1" ]; then
  MAIN=$1; shift
  exec java -cp "$ROOT/game/build/classes/java/main" "$MAIN" "$@" 2>&1 | grep -v '^Picked up'
fi
