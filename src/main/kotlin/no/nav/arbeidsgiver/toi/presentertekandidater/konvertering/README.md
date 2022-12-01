# Beskrivelse av konverteringen

## sql for konvertering fra arbeidsmarkeds database

## Oppretting av fil
- Kjør sql'er i vwmware image i oracle sql developer
- Eksporter filer som json

## Filnavn og plassering av fil
Migreringskoden forventer at uttrekkene legges i ./tmp mappen på poddene
- ./tmp/kandidater-konvertering.json
- ./tmp/kandidatlister-konvertering.json

## Starte konverteringsjobb
Når filene ligger på poden, kan vi kjøre
post mot http://<ingress>/internal/konverterdata

### Konverteringsjobb i dev
post: https://presenterte-kandidater-api.dev.intern.nav.no/internal/konverterdata

### Konverteringsjobb i prod
post: https://presenterte-kandidater-api.intern.nav.no/internal/konverterdata



