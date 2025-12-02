FROM gitpod/openvscode-server:latest

RUN sudo apt-get update && \
    sudo apt-get install -y --no-install-recommends libgomp1 libmpfr-dev openjdk-21-jre && \
    sudo apt-get clean && sudo rm -rf /var/lib/apt/lists/*

ADD examples /home/workspace/examples
RUN sudo chown openvscode-server -R /home/workspace

ADD theta-xsts-cli/ /theta-xsts-cli/
ENV PATH="$PATH:/theta-xsts-cli/"

ADD extensions/ /extensions/

RUN ${OPENVSCODE_SERVER_ROOT}/bin/openvscode-server --install-extension /extensions/*.vsix

ENTRYPOINT [ "/bin/sh", "-c", "exec ${OPENVSCODE_SERVER_ROOT}/bin/openvscode-server --host 0.0.0.0 --without-connection-token /home/workspace", "--" ]
