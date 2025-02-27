FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/presenterte-kandidater-api.tar /

# Force using the only the logback.xml file of this project to avoid accidental use of any logback.xml in it's dependencies, e.g. rapids-and-rivers
ENTRYPOINT ["java", "-Dlogback.configurationFile=src/main/resources/logback.xml", "-cp", "/presenterte-kandidater-api/lib/*", "no.nav.arbeidsgiver.toi.presentertekandidater.ApplicationKt"]

EXPOSE 9000
