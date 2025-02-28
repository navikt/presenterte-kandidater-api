FROM gcr.io/distroless/java21-debian12:nonroot
ADD build/distributions/presenterte-kandidater-api.tar /

# Important for Securelogs: The -cp command explicitly sets the order on the classpath to force using the logback.xml file of this project and to avoid accidental use of any logback.xml in it's dependencies, e.g. rapids-and-rivers.
# We did have an issue with logging meant for Securelogs accidentally ending up in ordinary applicaion logs.
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-cp", "/presenterte-kandidater-api/lib/presenterte-kandidater-api.jar;/presenterte-kandidater-api/lib/*", "no.nav.arbeidsgiver.toi.presentertekandidater.ApplicationKt"]

EXPOSE 9000
