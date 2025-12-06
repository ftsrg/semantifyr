# FROM gitpod/openvscode-server:latest
FROM jupyterhub/singleuser:latest

USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends libgomp1 libmpfr-dev openjdk-21-jre && \
    apt-get clean && sudo rm -rf /var/lib/apt/lists/*

ARG OVS_VER=v1.106.3
RUN wget -q https://github.com/gitpod-io/openvscode-server/releases/download/openvscode-server-${OVS_VER}/openvscode-server-${OVS_VER}-linux-x64.tar.gz \
 && tar xzf openvscode-server-${OVS_VER}-linux-x64.tar.gz \
 && mv openvscode-server-${OVS_VER}-linux-x64 /opt/openvscode-server \
 && ln -s /opt/openvscode-server/bin/openvscode-server /usr/local/bin/openvscode-server \
 && rm -f openvscode-server-${OVS_VER}-linux-x64.tar.gz

ADD examples /home/jovyan/examples
RUN chown -R jovyan /home/jovyan/examples

ADD theta-xsts-cli/ /theta-xsts-cli/
ENV PATH="$PATH:/theta-xsts-cli/"

USER jovyan

ADD extensions/ /extensions/

RUN openvscode-server --install-extension /extensions/*.vsix
RUN pip3 install --no-cache-dir jupyter-server-proxy

# ENTRYPOINT [ "/bin/sh", "-c", "exec ${OPENVSCODE_SERVER_ROOT}/bin/openvscode-server --host 0.0.0.0 --without-connection-token /home/workspace", "--" ]
ADD jupyter_server_config.py /etc/jupyter/jupyter_server_config.py
