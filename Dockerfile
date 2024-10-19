FROM gcr.io/distroless/java17-debian12:nonroot
ADD build/distributions/presenterte-kandidater-api.tar /
ENTRYPOINT ["java", "-cp", "/presenterte-kandidater-api/lib/*", "no.nav.arbeidsgiver.toi.presentertekandidater.ApplicationKt"]
EXPOSE 9000
