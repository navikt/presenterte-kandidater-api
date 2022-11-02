# presenterte-kandidater-api

Mottar API-kall fra presenterte-kandidater som henter kandidater som har lister med kandidater som presenteres for arbeidsgiver. Den tilbyr også diverse statusoppdateringer og sletting.
Endringer fra kandidatlisten i rekrutteringsbistand hentes inn til database som denne applikasjonen administrerer

# Utvikling

I testene bruker vi _TestContainers_, som krever at Docker kjører på maskinen. Da kan du enten bruke Docker Desktop eller Colima. Hvis du bruker Colima, må du legge til følgende shell-variabler for at TestContainers skal finne Docker-instansen:

```sh
# ~/.zshrc
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
```

Du må kanskje restarte IntelliJ etter disse innstillingene er lagret.

Før du kjører testene må du også huske å starte Colima med:
```sh
colima start
```

# Henvendelser

## For Nav-ansatte

- Dette Git-repositoriet eies av [Team inkludering i Produktområde arbeidsgiver](https://navno.sharepoint.com/sites/intranett-prosjekter-og-utvikling/SitePages/Produktomr%C3%A5de-arbeidsgiver.aspx).
- Slack-kanaler:
    - [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)
    - [#arbeidsgiver-utvikling](https://nav-it.slack.com/archives/CD4MES6BB)
    - [#arbeidsgiver-general](https://nav-it.slack.com/archives/CCM649PDH)

## For folk utenfor Nav

- Opprett gjerne en issue i Github for alle typer spørsmål
- IT-utviklerne i Github-teamet https://github.com/orgs/navikt/teams/arbeidsgiver-inkludering
- IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
