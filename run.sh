#!/bin/bash

# 1. Catch the exit of this script and kill all child processes
trap "kill 0" EXIT

# 2. Launch Server in the background
(cd ./ElvargServer && ./gradlew :game:run) &

# 3. Launch Client in the background
(cd ./ElvargClient && gamescope --force-grab-cursor ./gradlew run) &

# 4. Keep the main script alive so it can monitor and manage them
echo "Server and Client are running. Press Ctrl+C to stop both."
wait