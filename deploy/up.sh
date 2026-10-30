#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-edge}"
if [[ ! -f .env.production ]]; then
    echo 'Créer .env.production à partir de .env.production.example avant le lancement.' >&2
    exit 1
fi
bash deploy/init-secrets.sh
compose=(docker compose --env-file .env.production -f compose.production.yml)
case "$mode" in
    edge)
        # Never stop another web server or take its ports.
        if ! "${compose[@]}" --profile edge ps --status running --services | grep -qx edge; then
            if ss -H -ltn '( sport = :80 or sport = :443 )' | grep -q . \
                || ss -H -lun 'sport = :443' | grep -q .; then
                echo 'Les ports 80/443 sont déjà utilisés. Configurer le proxy existant avec le mode behind-proxy.' >&2
                exit 1
            fi
        fi
        compose+=(--profile edge)
        ;;
    behind-proxy) compose+=(-f compose.behind-proxy.yml) ;;
    *) echo 'Usage : bash deploy/up.sh [edge|behind-proxy]' >&2; exit 1 ;;
esac
"${compose[@]}" config --quiet
# Backup before any schema update. This also catches an inaccessible existing database.
if "${compose[@]}" ps --status running --services | grep -qx postgres; then
    bash deploy/backup.sh
fi
build_args=(--build)
if [[ "${CALIPSO_SKIP_BUILD:-0}" == 1 ]]; then
    build_args=(--no-build)
fi
"${compose[@]}" up --detach "${build_args[@]}" --wait --wait-timeout 300
"${compose[@]}" ps
