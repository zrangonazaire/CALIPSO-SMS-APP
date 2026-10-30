#!/usr/bin/env bash
set -euo pipefail
echo '--- Système et capacité ---'
id
uname -m
cat /etc/os-release
free -h
df -h /
echo '--- Services existants et ports ---'
ss -lntup
if command -v docker >/dev/null; then
    docker version --format '{{.Server.Version}}'
    docker compose version
    docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
else
    echo 'Docker absent : installer depuis le dépôt officiel Docker adapté à Ubuntu.'
fi
