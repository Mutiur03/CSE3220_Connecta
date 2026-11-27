#!/usr/bin/env bash
# One-time (and safe to re-run) setup of a fresh Ubuntu/Debian VPS for Connecta. Run as root:
#   sudo DEPLOY_USER=deploy DEPLOY_PUBKEY="ssh-ed25519 AAAA... github-actions" bash vps-bootstrap.sh
# What it does: installs Docker + compose plugin, creates a non-root deploy user (in the docker group, key-only
# login), enables the ufw firewall for 22/80/443, turns on unattended security upgrades, optionally installs
# fail2ban (INSTALL_FAIL2BAN=1, default on), creates /opt/connecta owned by the deploy user, and installs a daily backup cron job (/etc/cron.d/connecta-backup).
# It does NOT write the .env file, set DNS, or log in to GHCR: see docs/deployment.md.
# Careful: the deploy user is in the docker group, which is root-equivalent. Treat its SSH key as a root key.
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-deploy}"
DEPLOY_PUBKEY="${DEPLOY_PUBKEY:-}"
APP_DIR="${APP_DIR:-/opt/connecta}"
SSH_PORT="${SSH_PORT:-22}"
INSTALL_FAIL2BAN="${INSTALL_FAIL2BAN:-1}"

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)." >&2; exit 1; }
export DEBIAN_FRONTEND=noninteractive

install_docker() {
  if docker compose version >/dev/null 2>&1; then
    echo "Docker and the compose plugin are already installed."
    return
  fi
  apt-get update -y
  apt-get install -y ca-certificates curl
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
}

create_deploy_user() {
  id "$DEPLOY_USER" >/dev/null 2>&1 || adduser --disabled-password --gecos "" "$DEPLOY_USER"
  usermod -aG docker "$DEPLOY_USER"
  local ssh_dir="/home/$DEPLOY_USER/.ssh"
  install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$ssh_dir"
  touch "$ssh_dir/authorized_keys"
  chmod 600 "$ssh_dir/authorized_keys"
  chown "$DEPLOY_USER:$DEPLOY_USER" "$ssh_dir/authorized_keys"
  if [ -n "$DEPLOY_PUBKEY" ]; then
    grep -qxF "$DEPLOY_PUBKEY" "$ssh_dir/authorized_keys" || echo "$DEPLOY_PUBKEY" >> "$ssh_dir/authorized_keys"
  else
    echo "DEPLOY_PUBKEY not set: add the GitHub Actions public key to $ssh_dir/authorized_keys yourself." >&2
  fi
}

setup_firewall() {
  apt-get install -y ufw
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow "$SSH_PORT"/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw allow 443/udp # HTTP/3
  ufw --force enable
}

setup_updates() {
  apt-get install -y unattended-upgrades
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' \
    > /etc/apt/apt.conf.d/20auto-upgrades
}

setup_fail2ban() {
  [ "$INSTALL_FAIL2BAN" = 1 ] || return 0
  apt-get install -y fail2ban
  systemctl enable --now fail2ban
}

create_layout() {
  install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR"
}

# Daily database backup at 02:30 server time as the deploy user. The script is copied by the deploy workflow;
# until it exists the job fails (see backup.log), which is the reminder to deploy once.
setup_backup_cron() {
  printf '30 2 * * * %s /bin/bash %s/backup-postgres.sh >> %s/backup.log 2>&1\n' \
    "$DEPLOY_USER" "$APP_DIR" "$APP_DIR" > /etc/cron.d/connecta-backup
  chmod 644 /etc/cron.d/connecta-backup
}

apt-get update -y
install_docker
create_deploy_user
setup_firewall
setup_updates
setup_fail2ban
create_layout
setup_backup_cron

echo "Bootstrap done. Next: put .env in $APP_DIR (chmod 600), point DNS, add the GitHub secrets (docs/deployment.md)."
echo "Check that you can still log in as $DEPLOY_USER over SSH before you close this session."
