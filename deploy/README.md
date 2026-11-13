<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Deployment

One Ubuntu server runs the live editor's two instances (`semantifyr-live` and `semantifyr-live-staging`), the edge (`edge`) and the metrics stack (`observability`), each a Docker Compose project on the external network `edge`. The public hostnames reach Caddy through a Cloudflare Tunnel, and GitHub Actions deploy over SSH to the forced command `semantifyr-deploy`. The nightly suites run on a server through the forced command `semantifyr-nightly`.

## GitHub

- Environment `live-fured`, restricted to `main`: variables `DEPLOY_HOST`, `DEPLOY_PORT` and `DEPLOY_USER`, secrets `DEPLOY_SSH_KEY` and `DEPLOY_KNOWN_HOSTS` (`ssh-keygen -F '[<host>]:<port>'`).
- Environment `nightly-fured`, restricted to `main`: variables `NIGHTLY_HOST`, `NIGHTLY_PORT` and `NIGHTLY_USER`, secrets `NIGHTLY_SSH_KEY` and `NIGHTLY_KNOWN_HOSTS`, the same value as `DEPLOY_KNOWN_HOSTS` when the nightly runs on the deploy server.
- Repository secret `DOCKERHUB_TOKEN`: a Read & Write access token of the Docker Hub account the workflows log in as. The image repository must be public, since the server pulls without logging in.
- Repository secret `GRADLE_ENCRYPTION_KEY`: the key setup-gradle encrypts the configuration cache with (`openssl rand -base64 16`).
- Repository secret `SONAR_TOKEN`, optional: without it SonarCloud is skipped. SonarCloud deletes a token without an expiry after 60 days unused.
- Repository secret `VSCE_PAT`, optional: without it the release skips the marketplace.
- A GitHub OAuth app for the operator sign-in, with the callback `https://auth.semantifyr.org/oauth2/callback`.

## Cloudflare

The tunnel has one public hostname per site, each a published application with the service `http://caddy:80`: `live.semantifyr.org`, `live-staging.semantifyr.org`, `grafana.semantifyr.org` and `auth.semantifyr.org`. A hostname stays one level below `semantifyr.org`, since the edge certificate covers `*.semantifyr.org` only. A new hostname needs a site in `deploy/edge/Caddyfile`, and loses any DNS record or origin rule of its own before it is added to the tunnel.

## Server setup

Once per server, as an administrator with sudo.

1. Install the administrator's SSH key first, since step 8 turns passwords off, and copy `deploy/` to the server.
2. `apt install fail2ban ufw unattended-upgrades docker-compose-v2`: Ubuntu's `docker.io` has no compose plugin, and a copy in one user's `~/.docker` reaches neither root nor the deploy user. `/etc/apt/apt.conf.d/20auto-upgrades` with both periodic switches on, and a 2 GB `/swapfile` in fstab.
3. Merge `"log-driver": "local"`, `"log-opts": { "max-size": "20m", "max-file": "5" }` and `"live-restore": true` into `/etc/docker/daemon.json`; the restart briefly stops running containers.
4. A non-root deploy user in the docker group, `install -m 755 deploy/semantifyr-deploy /usr/local/bin/`, and in the deploy user's `~/.ssh/authorized_keys` the line `command="/usr/local/bin/semantifyr-deploy",restrict <public key>`. Generate the key pair locally, put the private half into `DEPLOY_SSH_KEY`, and delete it.
5. `docker network create --subnet 172.30.0.0/24 edge`. `/srv/edge` owned by the deploy user, with `.env` from `deploy/edge/env.example`, mode 600: `CF_TUNNEL_TOKEN` the tunnel's token, `GITHUB_USERS` the GitHub accounts allowed to sign in, comma-separated, `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` the OAuth app's, `COOKIE_SECRET` from `openssl rand -base64 32 | tr -- '+/' '-_'`. Then run Deploy Edge on `main`.
6. `/srv/observability` the same way, `.env` from `deploy/observability/env.example` with a random `GRAFANA_ADMIN_PASSWORD`, which nothing uses since the login form is off. Then run Deploy Observability on `main`. Its `node-exporter` mounts the host's `/proc`, `/sys` and `/` read-only, and the live containers' meters read their own cgroup, which needs cgroup v2 (`stat -fc %T /sys/fs/cgroup` prints `cgroup2fs`, the default from Ubuntu 22.04).
7. `/srv/semantifyr-live` and `/srv/semantifyr-live-staging` the same way, each `.env` from `deploy/live/env.example`: `INSTANCE` the directory name, `IMAGE_TAG` empty, and the capacity below. The next Continuous Delivery run starts staging, Deploy Production starts production.
8. ufw denies incoming and allows the SSH port. fail2ban's sshd jail on the port `sshd -T` reports; when every login arrives from one proxy address, put it in `ignoreip` or leave fail2ban out. `/etc/ssh/sshd_config.d/10-semantifyr.conf` with `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, `PermitRootLogin no` and `AllowUsers` naming the administrator and the deploy user; `10-` sorts before Ubuntu's `50-cloud-init.conf`, and sshd keeps the first value. `sshd -t`, reload, and test a fresh login before closing the session.

## Capacity

Per instance in its `.env`: `VERIFY_CONCURRENCY` (6 on production, 2 on staging), `THETA_JAVA_OPTS` (the heap of each Theta process; `JAVA_OPTS` changes nothing), `MEMORY_LIMIT` (`40g` and `12g`) and `CPUS` (8 and 2). A change applies at the instance's next deploy, or at once with `docker compose up -d` in its directory as the deploy user.

## Nightly

Once per server, as an administrator with sudo.

1. `adduser --disabled-password --comment '' nightly`, not in the docker group, and `nightly` added to `AllowUsers`; `sshd -t`, reload.
2. `install -m 755 deploy/semantifyr-nightly /usr/local/bin/`, and in `~nightly/.ssh/authorized_keys` (700 and 600, owned by `nightly`) the line `command="/usr/local/bin/semantifyr-nightly",restrict <public key>`. Generate the key pair locally, put the private half into `NIGHTLY_SSH_KEY`, and delete it.
3. Accept the licences: nuXmv's at https://nuxmv.fbk.eu/license.html (academic and non-commercial use), Uppaal's through the academic registration at https://uppaal.org/downloads, which mails the key.
4. `sudo sh deploy/nightly/install-engines.sh --accept-nuxmv-license --accept-uppaal-license`. Node.js 22 on `PATH` as well: the SysML suites start `node`, and the script installs none.
5. `sudo -u nightly -H ~nightly/tools/bin/verifyta --key <key>`. The key is leased to one machine for its lease period (48 hours by default) and renews over the Internet, so the server needs a key not leased elsewhere.
6. `sudo -u nightly -H sh -s < deploy/nightly/check-engines.sh` exits 0.
7. The first run by hand, in tmux: `sudo -u nightly -H env SSH_ORIGINAL_COMMAND="run $(git ls-remote https://github.com/ftsrg/semantifyr.git refs/heads/main | cut -f1)" /usr/local/bin/semantifyr-nightly`. Then run Nightly on `main`; its schedule takes over.

## Operating

- Production: run Deploy Production with no tag to promote what staging runs, or with a tag; an older tag rolls back. A deploy that does not turn healthy puts the previous tag back and fails; a failed smoke test rolls nothing back. An image that requires `SEMANTIFYR_LIVE_ADMIN_PASSWORD` does not start with `deploy/live/compose.yaml`, so production cannot roll back to one.
- Edge and observability: a push to `main` that changes their files deploys them, and a manual run redeploys. A change to a mounted file such as the Caddyfile recreates every service of the project, and the tunnel drops for a few seconds while cloudflared restarts. A failed start restores the previous files. To roll back, revert on `main`; at once, on the server as the deploy user, `cd /srv/edge && cp previous/* . && docker compose up -d --remove-orphans --force-recreate`, and the same in `/srv/observability`.
- Another operator: add the GitHub account to `GITHUB_USERS` in `/srv/edge/.env`, then run Deploy Edge.
- When Caddy breaks the routes: point the tunnel's live hostnames at `http://semantifyr-live:8080` and `http://semantifyr-live-staging:8080`. The editors work, the admin pages answer 403, and the handshake limit counts every client as cloudflared until Caddy is back.
- Metrics: Grafana at https://grafana.semantifyr.org behind the sign-in; Prometheus has no route. The dashboards are provisioned from `deploy/observability/*.json`, and Grafana does not save changes to them from its UI. After a deploy, `up` in Grafana's Explore is 1 for `semantifyr-live`, `semantifyr-live-staging` and `host`, and `semantifyr_live_container_cpu_limit_cores` shows each instance's `CPUS`.
- Load test: runs nightly against staging; run it by hand with the expected number of students before a class or a release.
- Release: run Release on `main` for a commit with a successful Continuous Delivery run, then raise `version` in `gradle.properties`.
