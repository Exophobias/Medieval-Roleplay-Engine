FROM maven:3.9-eclipse-temurin-25-alpine AS plugin-build

WORKDIR /mre-build
COPY . .
RUN mvn --batch-mode clean verify

FROM eclipse-temurin:25-jre-alpine

ARG PAPER_VERSION=26.2
ARG PAPER_BUILD=92
ARG PAPER_USER_AGENT="Exophobias-MRE-test-container/2.0 (https://github.com/Exophobias/Medieval-Roleplay-Engine)"
RUN apk add --no-cache curl jq \
    && builds="$(curl --fail --silent --show-error \
        --header "User-Agent: ${PAPER_USER_AGENT}" \
        "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds")" \
    && url="$(printf '%s' "$builds" | jq --exit-status --raw-output \
        --argjson build "$PAPER_BUILD" \
        'first(.[] | select(.id == $build) | .downloads."server:default".url)')" \
    && checksum="$(printf '%s' "$builds" | jq --exit-status --raw-output \
        --argjson build "$PAPER_BUILD" \
        'first(.[] | select(.id == $build) | .downloads."server:default".checksums.sha256)')" \
    && curl --fail --location --show-error \
        --header "User-Agent: ${PAPER_USER_AGENT}" \
        --output /paper.jar "$url" \
    && printf '%s  /paper.jar\n' "$checksum" | sha256sum --check --strict

COPY --from=plugin-build /mre-build/target/Medieval-Roleplay-Engine-*.jar /plugin.jar
COPY ./.testcontainer /resources
RUN chmod +x /resources/post-create.sh

WORKDIR /testmcserver
EXPOSE 25565
ENTRYPOINT ["/resources/post-create.sh"]
