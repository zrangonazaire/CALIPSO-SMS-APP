#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
secret_dir="${CALIPSO_SECRETS_DIR:-./deploy/.secrets}"
mkdir -p "$secret_dir"
chmod 700 "$secret_dir"
for name in db_password encryption_key bootstrap_password payment_webhook_secret; do
    if [[ ! -e "$secret_dir/$name" ]]; then
        openssl rand -base64 32 > "$secret_dir/$name"
    fi
    # The host directory is private; mounted files must be readable by the non-root application UID.
    chmod 444 "$secret_dir/$name"
done
echo 'Secrets présents. Aucune valeur affichée ; les fichiers existants sont conservés.'
