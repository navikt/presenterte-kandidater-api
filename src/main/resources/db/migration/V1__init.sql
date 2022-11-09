create table kandidatliste (
    id bigserial primary key,
    stilling_id uuid not null,
    tittel text not null,
    status text not null,
    slettet boolean not null,
    virksomhetsnummer varchar(9) not null,
    opprettet timestamp not null,
    sist_endret timestamp not null
);

create table kandidat (
    id bigserial primary key,
    aktør_id varchar(13) not null,
    kandidatliste_id bigint not null,
    opprettet timestamp not null default current_timestamp,
    constraint fk_kandidatliste_id foreign key (kandidatliste_id) references kandidatliste(id)
);

create table arbeidsgivers_vurdering (
    id bigserial primary key,
    kandidat_id bigserial not null,
    vurdering text not null,
    endret_av text not null,
    sist_endret timestamp not null default current_timestamp,
    constraint fk_kandidat_id foreign key (kandidat_id) references kandidat(id)
);

create index kandidatliste_stilling_id_idx on kandidatliste(stilling_id);
create index kandidatliste_virksomhetsnummer_idx on kandidatliste(virksomhetsnummer);
create index kandidat_kandidatliste_id_idx on kandidat(kandidatliste_id);
create index arbeidsgivers_vurdering_kandidat_id_idx on arbeidsgivers_vurdering(kandidat_id);
