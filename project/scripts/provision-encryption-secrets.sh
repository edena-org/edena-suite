#!/usr/bin/env bash
#
# Provision Edena encryption secrets on a fresh host:
#   master key -> /etc/edena/secrets/encryption-key
#   pepper     -> /var/lib/edena/secrets/encryption-pepper
#
# Random 32-byte base64 values, written 0600, owned by the service account, in
# SEPARATE directory trees (the pepper is a second trust boundary). Both live on
# persistent storage (survive reboot). Existing files are left untouched unless
# you pass --force.
#
# These paths/filenames match SymmetricCrypto.apply's fallback chain
# (EtcSecretsDir / VarLibSecretsDir + encryption-key / encryption-pepper).
#
# Usage:  sudo ./provision-encryption-secrets.sh <service-user> [--force]
#   e.g.  sudo ./provision-encryption-secrets.sh edena
#         sudo ./provision-encryption-secrets.sh peter --force

set -euo pipefail
umask 077

# --- args ---
SERVICE_USER=""
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    *)       SERVICE_USER="$arg" ;;
  esac
done
SERVICE_USER="${SERVICE_USER:-edena}"

KEY_DIR="/etc/edena/secrets";        KEY_FILE="$KEY_DIR/encryption-key"
PEPPER_DIR="/var/lib/edena/secrets"; PEPPER_FILE="$PEPPER_DIR/encryption-pepper"

# --- preconditions ---
[[ $EUID -eq 0 ]]                      || { echo "ERROR: run as root (sudo)." >&2; exit 1; }
command -v openssl >/dev/null          || { echo "ERROR: openssl not found." >&2; exit 1; }
id "$SERVICE_USER" >/dev/null 2>&1     || { echo "ERROR: user '$SERVICE_USER' does not exist." >&2; exit 1; }
GROUP="$(id -gn "$SERVICE_USER")"

gen_secret() {
  local dir="$1" file="$2" label="$3"

  if [[ -e "$file" && $FORCE -ne 1 ]]; then
    echo "SKIP  $label exists: $file  (use --force to replace — invalidates existing ciphertexts)"
    return 0
  fi

  install -d -m 755 "$(dirname "$dir")"                       # parent (e.g. /etc/edena), root:root 755
  install -d -m 750 -o "$SERVICE_USER" -g "$GROUP" "$dir"     # secrets dir, service-user 750

  local tmp; tmp="$(mktemp "$dir/.secret.XXXXXX")"            # same-fs temp -> atomic move
  openssl rand -base64 32 > "$tmp"
  chown "$SERVICE_USER:$GROUP" "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$file"
  echo "WROTE $label: $file"
}

gen_secret "$KEY_DIR"    "$KEY_FILE"    "master key"
gen_secret "$PEPPER_DIR" "$PEPPER_FILE" "pepper"

echo
echo "Owner=$SERVICE_USER:$GROUP  (perms below; secret values never printed)"
ls -ld "$KEY_DIR" "$PEPPER_DIR"
ls -l  "$KEY_FILE" "$PEPPER_FILE"
