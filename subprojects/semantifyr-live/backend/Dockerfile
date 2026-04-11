# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

FROM eclipse-temurin:25-jre-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends libgomp1 libmpfr-dev \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

COPY build/install/semantifyr-live-backend /opt/semantifyr/backend
COPY build/staging /opt/semantifyr/staging
COPY build/theta-xsts-cli /opt/theta-xsts-cli

ENV PATH="/opt/theta-xsts-cli:${PATH}"

ENV SEMANTIFYR_LIVE_LSP_BINARIES_DIR=/opt/semantifyr/staging/lsp
ENV SEMANTIFYR_LIVE_WEB_ROOT_DIR=/opt/semantifyr/staging/web
ENV SEMANTIFYR_LIVE_PORT=8080
ENV SEMANTIFYR_LIVE_ROOT_WORK_DIR=/tmp/semantifyr
ENV SEMANTIFYR_LIVE_ALLOWED_ORIGINS=localhost:5173,localhost:3000,localhost:8080

EXPOSE 8080

ENTRYPOINT ["/opt/semantifyr/backend/bin/semantifyr-live-backend", "start"]
