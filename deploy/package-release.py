"""Package current deployable sources without local credentials or generated output."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import tarfile

root = Path(__file__).resolve().parents[1]
out = root / "deploy" / "artifacts"
out.mkdir(parents=True, exist_ok=True)
stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
archive = out / f"calipso-{stamp}.tar.gz"
excluded_dirs = {".git", ".angular", "node_modules", "target", "dist", ".secrets", ".smoke", "artifacts", "backups", ".vscode"}
excluded_suffixes = {".log", ".key", ".pem", ".dump", ".sql", ".p12", ".pfx", ".jks"}
top_files = {"compose.production.yml", "compose.behind-proxy.yml", ".env.production.example"}

with tarfile.open(archive, "w:gz") as tar:
    for source in sorted(root.rglob("*")):
        relative = source.relative_to(root)
        if any(part in excluded_dirs for part in relative.parts):
            continue
        if not source.is_file():
            continue
        if relative.parts[0] not in {"calipso-backend", "calipso-frontend", "deploy"} and str(relative) not in top_files:
            continue
        if source.name.endswith(".local.properties") or source.suffix.lower() in excluded_suffixes:
            continue
        if source.name.startswith(".env") and source.name != ".env.production.example":
            continue
        tar.add(source, arcname=relative.as_posix(), recursive=False)

checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix(archive.suffix + ".sha256").write_text(f"{checksum}  {archive.name}\n", encoding="utf-8")
print(archive)
print(f"SHA-256: {checksum}")
