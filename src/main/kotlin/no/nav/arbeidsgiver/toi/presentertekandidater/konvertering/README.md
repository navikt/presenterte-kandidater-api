# Beskrivelse av konverteringen


## Uttrekk fra gammel database
Den gamle databasen er en on-prem Oracle database. For å hente ut data kan man f.eks kjøre spørringene
under i Oracle SQL Developer (i VDI) og eksportere de som JSON:

### Hente ut kandidatlister

```
select to_char(l.opprettet_tidspunkt, 'yyyy-mm-dd hh:mi:ss') as opprettet_tidspunkt, l.organisasjon_referanse as organisasjon_referanse,
l.stilling_id as stilling_id, l.tittel as tittel
from AGKANDLISTE l
where
l.db_id in (select distinct db_id from agkandliste ll
  where ll.stilling_id = l.stilling_id and
  ll.stilling_id is not null and ll.organisasjon_referanse is not null and ll.tittel is not null
  and ll.opprettet_av='NAV' and
  ll.opprettet_tidspunkt in( select max(lll.opprettet_tidspunkt) from agkandliste lll where lll.stilling_id=ll.stilling_id )
  )
order by l.opprettet_tidspunkt desc;
```

### Hente ut kandidater

```
select distinct(k.kandidatnr) as kandidatnr, to_char(k.lagt_til_tidspunkt, 'yyyy-mm-dd hh:mi:ss') as lagt_til_tidspunkt,
  l.stilling_id as stilling_id, k.kandidatstatus as kandidatstatus, to_char(k.lagt_til_tidspunkt, 'yyyy-mm-dd hh:mi:ss') as endret_tidspunkt
  from agkandidat k, AGKANDLISTE l
  where
  k.endret_tidspunkt > to_date('2022-05-01', 'yyyy-mm-dd')
  and exists (select 1 from AGKANDLISTE ll
    where ll.stilling_id is not null and ll.organisasjon_referanse is not null and ll.tittel is not null
    and k.agkandliste_db_id = ll.db_id
    and ll.opprettet_av='NAV'
    and ll.stilling_id=l.stilling_id
    and ll.opprettet_tidspunkt > to_date('2022-05-01', 'yyyy-mm-dd'))
    order by k.kandidatnr, l.stilling_id, endret_tidspunkt desc;
```


Hvis men bruker JSON eksport i Oracle SQL Developer så må man fjerne den første linje og den siste }
Hvis man har perl installert så kan dette gjøres med:

```
  cat kandidater.json | perl -e '<>; $_=join("", <>);chop;print'
```

Hvis du i tillegg ønsker pen og lesbar output, så legger du på en pipe til jq . :

```
  cat kandidater.json | perl -e '<>; $_=join("", <>);chop;print' | jq .
```

## Oppretting av fil
- Kjør sql'er i vwmware image i oracle sql developer
- Eksporter filer som json

## Filnavn og plassering av fil
Migreringskoden forventer at uttrekkene legges i /tmp mappen på poddene
- /tmp/kandidater-konvertering.json
- /tmp/kandidatlister-konvertering.json

## Starte konverteringsjobb
Når filene ligger på poden, kan vi kjøre
post mot http://<ingress>/internal/konverterdata

### Konverteringsjobb i dev
(Logg inn på pod)

```
  wget --method=POST http://localhost:9000/internal/konverterdata
```

### Konverteringsjobb i prod@
(Logg inn på pod)

```
  wget --method=POST http://localhost:9000/internal/konverterdata
```
