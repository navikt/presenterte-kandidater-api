FROM navikt/java:17
COPY ./build/libs/presenterte-kandidater-api-all.jar app.jar

EXPOSE 8333
