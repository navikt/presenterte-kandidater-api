create table kandidatliste (
    id bigserial primary key,
    uuid uuid not null,
    stilling_id uuid not null,
    tittel text not null,
    status text not null,
    slettet boolean not null,
    virksomhetsnummer varchar(9) not null,
    opprettet timestamp with time zone not null default current_timestamp,
    sist_endret timestamp with time zone not null
);

create table kandidat (
    id bigserial primary key,
    uuid uuid not null,
    aktør_id varchar(13) not null,
    kandidatliste_id bigint not null,
    opprettet timestamp with time zone not null default current_timestamp,
    arbeidsgivers_vurdering text not null,
    sist_endret timestamp with time zone not null,
    constraint fk_kandidatliste_id foreign key (kandidatliste_id) references kandidatliste(id)
);

create index kandidatliste_stilling_id_idx on kandidatliste(stilling_id);
create index kandidatliste_uuid_id_idx on kandidatliste(uuid);
create index kandidat_uuid_id_idx on kandidat(uuid);
create index kandidatliste_virksomhetsnummer_idx on kandidatliste(virksomhetsnummer);
create index kandidat_kandidatliste_id_idx on kandidat(kandidatliste_id);
