# presenterte-kandidater-api

Mottar API-kall fra presenterte-kandidater som henter kandidater som har lister med kandidater som presenteres for
arbeidsgiver. Den tilbyr også diverse statusoppdateringer og sletting.
Endringer fra kandidatlisten i rekrutteringsbistand hentes inn til database som denne applikasjonen administrerer

## Innlogging som arbeidsgiver i dev

For å logge inn som arbeidsgiver som representerer en gitt virksomhet kan man:

- Finne organisasjonsnummeret til hovedenheten i JSON-responsen til rekrutteringsbistand-stilling
- Søke opp bedriften i Tenor slik: ```organisasjonsnummer:312113341```
- I "Kildedata" finner man feltet "rollegrupper" som blant annet inneholder fødselsnummeret til daglig leder
- Fødselsnummeret til daglig leder kan brukes for å logge inn med "Testid" i ID-porten

NB: Dersom man ved innlogging blir møtt av skjerm for utfylling av kontaktinformasjon, kan det ta noe tid før
testmiljøet til Altinn returnerer riktige roller. Det vil derfor midlertidig framstå som om daglig leder ikke har noen
roller.

# Utvikling

I testene bruker vi _TestContainers_, som krever at Docker kjører på maskinen. Da kan du enten bruke Docker Desktop
eller Colima. Hvis du bruker Colima, må du legge til følgende shell-variabler for at TestContainers skal finne
Docker-instansen:

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

* Dette Git-repositoriet eies
  av [team Toi i produktområde Arbeidsgiver](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
* Slack-kanaler:
    * [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)
    * [#rekrutteringsbistand-værsågod](https://nav-it.slack.com/archives/C02HWV01P54)

## For folk utenfor Nav

IT-avdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)

