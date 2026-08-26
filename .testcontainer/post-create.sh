#!/bin/sh
set -eu

server_jar=/testmcserver/paper-26.2-build-92.jar
mkdir -p /testmcserver/plugins

if [ ! -f "$server_jar" ]; then
    cp /paper.jar "$server_jar"
fi

# Refresh only this plugin when a persisted test-server directory is reused.
cp /plugin.jar /testmcserver/plugins/Medieval-Roleplay-Engine.jar

if [ ! -f /testmcserver/ops.json ]; then
    cp /resources/ops.json /testmcserver/ops.json
fi
printf 'eula=true\n' > /testmcserver/eula.txt

exec java -jar "$server_jar" --nogui
