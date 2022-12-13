create table samtykke (
      id bigserial primary key,
      fødselsnummer char(11) not null unique,
      opprettet timestamp with time zone not null
)