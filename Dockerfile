FROM ghcr.io/navikt/baseimages/temurin:17
COPY ./build/libs/presenterte-kandidater-api-all.jar app.jar

EXPOSE 9000
