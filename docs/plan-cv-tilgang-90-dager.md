# Plan: arbeidsgivere ser oversendte CV-er i 90 dager

Arbeidsgivere skal se CV-er som er sendt til dem på «Min side – arbeidsgiver» i 90 dager, ikke seks måneder som i dag. Tiltaket kommer fra ROS-analysen for kandidatlister til arbeidsgivere. Få rekrutteringsoppdrag varer et halvt år, så arbeidsgiver trenger sjelden CV-ene så lenge.

Markedskontakter og arbeidsgivere får informasjon samme dag som endringen rulles ut, ikke på forhånd.

## Slik virker det i dag

- `slettejobb.kt` sletter kandidater fra kandidatlistene når `opprettet` er eldre enn seks måneder (`seksMånederSiden()`).
- Jobben starter ett minutt etter at appen er oppe, og kjører deretter hver time.
- Kandidaten slettes fra databasen (`slettKandidat`). Det finnes ingen myk sletting.
- Endepunktet for arbeidsgiver i `controller.kt` henter CV-er for kandidatene som fortsatt ligger i lista. Det filtrerer ikke på alder selv, så slettejobben er det eneste som styrer hvor lenge en CV er synlig.
- Den samme hjelpefunksjonen brukes også til å markere tomme kandidatlister som slettet når `sist_endret` er eldre enn seks måneder.

`foresporsel-om-deling-av-cv-api` (FSS) og `toi-cvdeling-samtykkestatus` (GCP) har ingen seksmånedersregel. De 120 dagene i `SamtykkeBesvartLytter.kt` gjelder hvor lenge et samtykke varer og berøres ikke av denne endringen. Overgangen fra FSS til GCP påvirker derfor ikke denne oppgaven.

## Avklaringer før vi starter

- [x] Tomme kandidatlister skal også markeres som slettet etter 90 dager (avklart 23.11.2026). Begge reglene i `slettejobb.kt` bruker samme terskel.
- [ ] Velg dato for utrulling. Datoen erstatter «xx.xx.xx» i alle tekstene under.
- [ ] Vilkårene arbeidsgiver godtar i `presenterte-kandidater` (`app/samtykke/page.tsx`), sier at CV-ene slettes etter seks måneder. Samtykket lagres uten versjon (`V3__samtykke.sql`: bare fødselsnummer og tidspunkt), så arbeidsgivere som har godtatt, blir ikke bedt om å godta på nytt. Avklar med jurist eller personvernombud om det er greit at infoboksen er nok informasjon, eller om eksisterende samtykker må fornyes.

Frontenden for «Min side – arbeidsgiver» er [`navikt/presenterte-kandidater`](https://github.com/navikt/presenterte-kandidater). Den kaller `presenterte-kandidater-api` gjennom `app/api/presenterte-kandidater-api/`.

## Sjekkliste

### Før utrulling

- [ ] Gi Petter Ravik (NKS Nordland Mo) beskjed om datoen. Han vet at endringen kommer, men ikke når. Infoboksen viser til arbeidsgivertelefonen, så NKS må vite om endringen før arbeidsgiverne ser den.
- [ ] Gi brukerstøtte beskjed om datoen.

### Kode i `presenterte-kandidater-api`

- [ ] Bytt `seksMånederSiden()` i `slettejobb.kt` med `nittiDagerSiden(): ZonedDateTime = ZonedDateTime.now().minusDays(90)`. Den brukes både for kandidater (`opprettet`) og for tomme kandidatlister (`sist_endret`). Bruk dager, ikke `minusMonths(3)`, fordi tre måneder varierer mellom 89 og 92 dager. Teamet har bestemt 90 dager (Trello-kortet sa opprinnelig 91).
- [ ] Oppdater `SlettejobbTest.kt`:
  - en kandidat opprettet for 90 dager siden blir slettet
  - en kandidat opprettet for 89 dager siden blir ikke slettet
  - en tom kandidatliste som ikke er endret på 90 dager, blir markert som slettet
  - en tom kandidatliste som ble endret for 89 dager siden, blir ikke markert som slettet
  - gi testene nye navn, så de ikke lenger sier «6 måneder»
- [ ] Kjør `./gradlew test`.

### Kode i `presenterte-kandidater` (frontend)

Repoet har bare én tekst om seks måneder. Tester, mocks og README nevner ikke tiden.

Endringene ligger på branchen `kortere-cv-tilgang-91-dager` (navnet er beholdt selv om terskelen nå er 90 dager). Branchen deployes til dev via `deploy-dev-branch` i `.github/workflows/workflow.yaml`. Sett den tilbake før merge til `main`.

- [x] Endre vilkårsteksten i `app/samtykke/page.tsx` (linje 134) fra «CV-ene slettes automatisk seks måneder etter at de er mottatt.» til «CV-ene slettes automatisk 90 dager etter at de er mottatt.»
- [x] Legg inn infoboks med Aksel `InfoCard` (`data-color="info"`) øverst i `app/Kandidatlister.tsx`, over «Aktive rekrutteringsprosesser». Komponenten ligger i `app/components/EndretVisningstidInfo.tsx`. `Alert` er utfaset i Aksel. Setningen «Endringen gjelder fra xx.xx.xx.» er tatt ut, siden boksen sier «nå». Tekst:

  > **Endring i tilgjengelighet for oversendte CV-er**
  >
  > Oversendte CV-er vil nå være tilgjengelige på denne siden i 90 dager, mot tidligere seks måneder.
  >
  > Endringen er gjort etter en ny vurdering av hvor lenge opplysningene er nødvendige for å ivareta formålet med rekrutteringsprosessen, i samsvar med GDPR.
  >
  > Dersom den nye tilgjengelighetsperioden medfører utfordringer for dere, kan dere kontakte arbeidsgivertelefonen på 55 55 33 36.

- [x] Kjør eslint, prettier, `tsc` og Playwright. Alt er grønt. Infoboksen trenger `[overflow-wrap:anywhere]`, ellers kuttes lange ord ved 200 % tekststørrelse på mobil (WCAG 1.4.4).
- [ ] Fjern `EndretVisningstidInfo` seks uker etter utrulling (eget Trello-kort).

### Utrulling

> ⚠️ **Slettingen kan ikke reverseres.** Ett minutt etter at den nye versjonen starter, sletter slettejobben alle kandidater som ble delt for mellom 90 dager og seks måneder siden. Kandidatene slettes fra databasen og kan ikke hentes tilbake ved å rulle tilbake koden. Samtidig markeres tomme kandidatlister som ikke er endret på 90 dager, som slettet (`slettet = true`). Det er myk sletting og kan rettes i databasen om nødvendig. Sjekk at datoen er avklart og at NKS og brukerstøtte har fått beskjed før du merger.

- [ ] Rull ut infoboksen, den nye vilkårsteksten og endringen i backend samme dag. Rull ut frontenden først, så arbeidsgiverne ser forklaringen før CV-ene forsvinner.
- [ ] Følg med i loggene etter første kjøring: «Skal slette N kandidater». Antallet skal være høyt første gang og lavt etterpå.
- [ ] Sjekk at arbeidsgiversiden fortsatt viser CV-er som er yngre enn 90 dager.

### Samme dag som utrulling

- [ ] Legg inn nyhet i Rekrutteringsbistand via admin-funksjonen for nyheter (`nyhet-admin.ts`). Tekst:

  > **Tilgangen til CV-er for arbeidsgivere er redusert fra seks til tre måneder**
  >
  > Tilgangen til oversendte CV-er til arbeidsgivere er redusert fra seks til tre måneder fra i dag xx.xx.xx. Bakgrunn er at det er gjort en ny vurdering hva som er tilstrekkelig tid for arbeidsgiver å ha tilgang til CV-en for rekrutteringsoppdraget i henhold til GDPR.

- [ ] Per Erik skriver og poster nyhet i Teams-kanalen Markedsnettverket i Nav og merker alle.

### Etter utrulling

- [ ] Lag et Trello-kort for å fjerne infoboksen fra `app/Kandidatlister.tsx` i `presenterte-kandidater`. Vilkårsteksten skal stå.
- [ ] Per Erik legger inn en påminnelse i Outlook seks uker etter utrullingsdatoen, så infoboksen fjernes da.
