create table kandidatliste (
    id bigserial primary key,
    stilling_id uuid not null,
    tittel text not null,
    status text not null,
    slettet boolean not null,
    virksomhetsnummer varchar(9) not null
);

create table kandidat (
    id bigserial primary key,
    aktør_id varchar(13) not null,
    kandidatliste_id bigint not null,
    hendelsestidspunkt timestamp not null,
    hendelsestype text not null,
    arbeidsgivers_status text not null,
    constraint fk_kandidatliste_id foreign key (kandidatliste_id) references kandidatliste(id)
);

create index kandidatliste_stilling_id_idx on kandidatliste(stilling_id);
create index kandidatliste_virksomhetsnummer_idx on kandidatliste(virksomhetsnummer);
create index kandidat_kandidatliste_id_idx on kandidat(kandidatliste_id);