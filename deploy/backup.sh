#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
backup_dir="${CALIPSO_BACKUP_DIR:-./deploy/backups}"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$backup_dir/calipso-$stamp.dump"
docker compose --env-file .env.production -f compose.production.yml exec -T postgres \
    pg_dump --username=calipso --dbname=calipso --format=custom --no-owner --no-acl > "$file.partial"
test -s "$file.partial"
mv "$file.partial" "$file"
sha256sum "$file" > "$file.sha256"
chmod 600 "$file" "$file.sha256"
echo "Sauvegarde créée : $file"
echo 'Conserver également les secrets, notamment encryption_key, dans une sauvegarde séparée et protégée.'
