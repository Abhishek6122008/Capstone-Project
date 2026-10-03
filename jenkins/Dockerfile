# Jenkins LTS (ships JDK 21, which the Maven wrapper uses) + Docker CLI
FROM jenkins/jenkins:lts-jdk21

USER root
# Only the Docker client; images are built by the host's Docker daemon via the mounted socket
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker/buildx-bin /buildx /usr/libexec/docker/cli-plugins/docker-buildx
USER jenkins

# Plugins the Jenkinsfile needs, pre-installed so the setup is reproducible
RUN jenkins-plugin-cli --plugins \
    workflow-aggregator \
    git \
    github \
    credentials-binding \
    junit \
    pipeline-stage-view
