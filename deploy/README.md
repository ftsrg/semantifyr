<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Deployment

One Ubuntu server runs the two live instances (`semantifyr-live`, `semantifyr-live-staging`), the edge and the metrics stack as Docker Compose projects on the network `edge`. A Cloudflare Tunnel reaches Caddy. GitHub Actions deploy over SSH to the forced command `semantifyr-deploy` and start the nightly through `semantifyr-nightly`.

## GitHub

- Environment `live-fured`, restricted to `main`: variables `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`; secrets `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` (`ssh-keygen -F '[<host>]:<port>'`).
- Environment `nightly-fured`, restricted to `main`: variables `NIGHTLY_HOST`, `NIGHTLY_PORT`, `NIGHTLY_USER`; secrets `NIGHTLY_SSH_KEY`, `NIGHTLY_KNOWN_HOSTS`.
- Repository secrets: `DOCKERHUB_TOKEN` (Read & Write; the image repository is public), `GRADLE_ENCRYPTION_KEY` (`openssl rand -base64 16`), and optionally `SONAR_TOKEN` and `VSCE_PAT`.
- A GitHub OAuth app for the operator sign-in, with the callback `https://auth.semantifyr.org/oauth2/callback`.

## Cloudflare

The tunnel has one public hostname per site, each with the service `http://caddy:80`: `live.semantifyr.org`, `live-staging.semantifyr.org`, `grafana.semantifyr.org` and `auth.semantifyr.org`. A new hostname stays one level below `semantifyr.org` and needs a site in `deploy/edge/Caddyfile`.

## Server setup

Once per server, as an administrator with sudo.

1. Install the administrator's SSH key and copy `deploy/` to the server.
2. `apt install fail2ban ufw unattended-upgrades docker-compose-v2`; `/etc/apt/apt.conf.d/20auto-upgrades` with both periodic switches on; a 2 GB `/swapfile` in fstab.
3. Merge `"log-driver": "local"`, `"log-opts": { "max-size": "20m", "max-file": "5" }` and `"live-restore": true` into `/etc/docker/daemon.json`.
4. A non-root deploy user in the docker group; `install -m 755 deploy/semantifyr-deploy /usr/local/bin/`; in its `~/.ssh/authorized_keys` the line `command="/usr/local/bin/semantifyr-deploy",restrict <public key>`, with the private half in `DEPLOY_SSH_KEY`.
5. `docker network create --subnet 172.30.0.0/24 edge`. `/srv/edge` owned by the deploy user, `.env` from `deploy/edge/env.example`, mode 600, `COOKIE_SECRET` from `openssl rand -base64 32 | tr -- '+/' '-_'`. Run Deploy Edge on `main`.
6. `/srv/observability` the same way, `.env` from `deploy/observability/env.example`. Run Deploy Observability on `main`.
7. `/srv/semantifyr-live` and `/srv/semantifyr-live-staging` the same way, `.env` from `deploy/live/env.example`: `INSTANCE` the directory name, `IMAGE_TAG` empty, and the capacity below.
8. ufw denies incoming and allows the SSH port; fail2ban's sshd jail has the proxy's address in `ignoreip`. `/etc/ssh/sshd_config.d/10-semantifyr.conf` with `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PermitRootLogin no` and `AllowUsers` naming the administrator and the deploy user. `sshd -t`, reload, and test a fresh login before closing the session.

## Capacity

Per instance in its `.env`: `VERIFY_CONCURRENCY` (6 on production, 2 on staging), `VERIFY_TIMEOUT_SECONDS`, `THETA_JAVA_OPTS`, `MEMORY_LIMIT` (`40g`, `12g`) and `CPUS` (8, 2). A change applies at the next deploy, or at once with `docker compose up -d` in the instance's directory.

## Nightly

Once per server, as an administrator with sudo.

1. `adduser --disabled-password --comment '' nightly`, not in the docker group; `nightly` added to `AllowUsers`.
2. `install -m 755 deploy/semantifyr-nightly /usr/local/bin/`; in `~nightly/.ssh/authorized_keys` the line `command="/usr/local/bin/semantifyr-nightly",restrict <public key>`, with the private half in `NIGHTLY_SSH_KEY`.
3. `sudo sh deploy/nightly/install-engines.sh --accept-nuxmv-license --accept-uppaal-license`, which accepts nuXmv's and Uppaal's academic licences. Node.js 22 on the user's `PATH` as well.
4. `sudo -u nightly -H ~nightly/tools/bin/verifyta --key <key>`; the key is leased to one machine.
5. `sudo -u nightly -H sh -s < deploy/nightly/check-engines.sh` exits 0.
6. The first run by hand, in tmux: `sudo -u nightly -H env SSH_ORIGINAL_COMMAND="run $(git ls-remote https://github.com/ftsrg/semantifyr.git refs/heads/main | cut -f1)" /usr/local/bin/semantifyr-nightly`. Then run Nightly on `main`.

## Operating

- Production: run Deploy Production with no tag to promote what staging runs, or with a tag; an older tag rolls back.
- Edge and observability: a push to `main` that changes their files deploys them. To roll back at once, as the deploy user: `cd /srv/edge && cp previous/* . && docker compose up -d --remove-orphans --force-recreate`, and the same in `/srv/observability`.
- Another operator: add the GitHub account to `GITHUB_USERS` in `/srv/edge/.env`, then run Deploy Edge.
- When Caddy breaks the routes: point the tunnel's live hostnames at `http://semantifyr-live:8080` and `http://semantifyr-live-staging:8080`.
- Metrics: Grafana at https://grafana.semantifyr.org behind the sign-in; each `deploy/observability/live-*.json` is the source of one dashboard, which Deploy Observability writes once for production and once for staging.
- Release: run Release on `main` for a commit with a successful Continuous Delivery run, then raise `version` in `gradle.properties`.
