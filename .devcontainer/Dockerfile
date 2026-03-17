FROM gradle:8.2-jdk17

RUN apt-get update
RUN apt-get install -y sudo less jq

# Prevent collisions with remote user
RUN usermod -u 999 gradle
RUN groupmod -g 999 gradle

ARG REMOTE_USER
ARG REMOTE_UID
ARG REMOTE_GID

RUN groupadd --gid $REMOTE_GID $REMOTE_USER \
    && useradd --uid $REMOTE_UID --gid $REMOTE_GID -m $REMOTE_USER -s /bin/bash

USER $REMOTE_USER
