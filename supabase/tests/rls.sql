-- Isolation entre comptes, droits, contraintes CHECK et Realtime du schéma `cartouche`.
-- Chaque vérification lève une exception en cas d'écart.

insert into auth.users (id, email) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'a@exemple.fr'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'b@exemple.fr');

create function pg_temp.connecte_comme(uid uuid) returns void
language plpgsql as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', uid)::text, true);
    set local role authenticated;
end;
$$;

-- La requête doit être refusée **pour la bonne raison** : un refus d'un autre code SQLSTATE
-- (droit manquant au lieu d'une contrainte, par exemple) ferait passer un test à tort.
--   23502 not null · 23505 clé dupliquée · 23514 CHECK · 42501 droit ou RLS refusé
create function pg_temp.refuse(requete text, motif text, sqlstate_attendu text) returns void
language plpgsql as $$
begin
    begin
        execute requete;
    exception when others then
        if sqlstate <> sqlstate_attendu then
            raise exception 'Refus pour le mauvais motif (% au lieu de %) (%) : %',
                sqlstate, sqlstate_attendu, motif, requete;
        end if;
        return;
    end;
    raise exception 'Requête acceptée alors qu''elle devait être refusée (%) : %', motif, requete;
end;
$$;

-- Insère un jeu minimal en fixant une colonne (valeur SQL littérale) : la requête est acceptée
-- (`accepte_valeur`) ou refusée par la contrainte CHECK (`refuse_valeur`).
create function pg_temp.requete_valeur(colonne text, valeur text) returns text
language sql as $$
    select format(
        'insert into cartouche.game (id, title, platform, genre, status, %I) '
        'values (gen_random_uuid(), ''x'', ''x'', ''x'', ''A_FAIRE'', %s)', colonne, valeur)
$$;

create function pg_temp.accepte_valeur(colonne text, valeur text) returns void
language plpgsql as $$
begin
    execute pg_temp.requete_valeur(colonne, valeur);
end;
$$;

create function pg_temp.refuse_valeur(colonne text, valeur text) returns void
language plpgsql as $$
begin
    perform pg_temp.refuse(pg_temp.requete_valeur(colonne, valeur),
        colonne || ' = ' || valeur, '23514');
end;
$$;

-- A crée un jeu ; user_id vient de auth.uid() par défaut, les colonnes à valeur par défaut
-- prennent leur valeur.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game (id, title, platform, genre, status)
    values ('11111111-1111-1111-1111-111111111111', 'Jeu de A', 'PC', 'RPG', 'A_FAIRE');
do $$ begin
    assert (select user_id from cartouche.game) = 'aaaaaaaa-0000-0000-0000-000000000001', 'user_id par défaut';
    assert (select user_playtime_hours from cartouche.game) = 0, 'temps de jeu par défaut';
    assert (select notes from cartouche.game) = '', 'notes par défaut';
    assert (select played_platforms from cartouche.game) = '{}', 'plateformes jouées par défaut';
    assert (select igdb_lookup_attempted from cartouche.game) = false, 'drapeau IGDB par défaut';
end $$;
commit;

-- B ne voit rien, ne modifie rien, ne supprime rien.
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
do $$ begin
    assert (select count(*) from cartouche.game) = 0, 'B voit un jeu de A';
end $$;
update cartouche.game set title = 'piraté';
delete from cartouche.game;
select pg_temp.refuse(
    $q$insert into cartouche.game (id, user_id, title, platform, genre, status)
       values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'TERMINE')$q$,
    'insertion au nom de A', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game (id, title, platform, genre, status)
       values ('11111111-1111-1111-1111-111111111111', 'écrase', 'x', 'x', 'TERMINE')
       on conflict (id) do update set title = excluded.title$q$,
    'upsert sur l''identifiant de A', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game (id, title, platform, genre, status)
       values ('11111111-1111-1111-1111-111111111111', 'doublon', 'x', 'x', 'TERMINE')$q$,
    'même identifiant que A, sans upsert', '23505');
commit;

-- Rien n'a bougé pour A.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
do $$ begin
    assert (select title from cartouche.game) = 'Jeu de A', 'le jeu de A a été modifié';
    assert (select count(*) from cartouche.game) = 1, 'les jeux de A ont changé';
end $$;
-- A ne peut pas céder une ligne à B.
select pg_temp.refuse(
    $q$update cartouche.game set user_id = 'bbbbbbbb-0000-0000-0000-000000000002'$q$,
    'changement de propriétaire', '42501');
-- A peut modifier son jeu, et rejouer un upsert sans créer de doublon (écriture idempotente).
update cartouche.game set status = 'EN_COURS', rating = 4;
insert into cartouche.game (id, title, platform, genre, status)
    values ('11111111-1111-1111-1111-111111111111', 'Jeu de A', 'PC', 'RPG', 'TERMINE')
    on conflict (id) do update set status = excluded.status;
do $$ begin
    assert (select count(*) from cartouche.game) = 1, 'upsert : doublon créé';
    assert (select status from cartouche.game) = 'TERMINE', 'upsert : statut non mis à jour';
end $$;
-- Supprimer une ligne absente n'est pas une erreur (rejeu d'une suppression).
delete from cartouche.game where id = gen_random_uuid();
commit;

-- Contraintes CHECK : reprise, borne par borne, de firestore.rules (jamais plus strictes).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');

-- Obligatoires : title, platform, genre, status, igdbLookupAttempted (et l'identifiant).
select pg_temp.refuse($q$insert into cartouche.game (title, platform, genre, status) values ('x', 'x', 'x', 'A_FAIRE')$q$, 'identifiant absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'titre absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, genre, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'plateforme absente', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, status) values (gen_random_uuid(), 'x', 'x', 'A_FAIRE')$q$, 'genre absent', '23502');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre) values (gen_random_uuid(), 'x', 'x', 'x')$q$, 'statut absent', '23502');
select pg_temp.refuse(pg_temp.requete_valeur('igdb_lookup_attempted', 'null'), 'drapeau IGDB nul', '23502');

-- Statut : quatre valeurs, rien d'autre (la casse compte).
insert into cartouche.game (id, title, platform, genre, status) values
    (gen_random_uuid(), 'x', 'x', 'x', 'A_FAIRE'),
    (gen_random_uuid(), 'x', 'x', 'x', 'EN_COURS'),
    (gen_random_uuid(), 'x', 'x', 'x', 'TERMINE'),
    (gen_random_uuid(), 'x', 'x', 'x', 'ABANDONNE');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', 'VU')$q$, 'statut inconnu', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', 'termine')$q$, 'statut en minuscules', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', 'x', '')$q$, 'statut vide', '23514');

-- Textes : un titre vide était accepté par les anciennes règles, il l'est ici.
insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), '', '', '', 'A_FAIRE');
insert into cartouche.game (id, title, platform, genre, status)
    values (gen_random_uuid(), repeat('é', 300), repeat('é', 200), repeat('é', 100), 'A_FAIRE');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), repeat('x', 301), 'x', 'x', 'A_FAIRE')$q$, 'titre de 301 caractères', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', repeat('x', 201), 'x', 'A_FAIRE')$q$, 'plateforme de 201 caractères', '23514');
select pg_temp.refuse($q$insert into cartouche.game (id, title, platform, genre, status) values (gen_random_uuid(), 'x', 'x', repeat('x', 101), 'A_FAIRE')$q$, 'genre de 101 caractères', '23514');
select pg_temp.accepte_valeur('notes', 'repeat(''x'', 5000)');
select pg_temp.refuse_valeur('notes', 'repeat(''x'', 5001)');
select pg_temp.refuse(pg_temp.requete_valeur('notes', 'null'), 'notes nulles', '23502');
select pg_temp.accepte_valeur('cover_url', 'repeat(''x'', 2000)');
select pg_temp.refuse_valeur('cover_url', 'repeat(''x'', 2001)');
select pg_temp.accepte_valeur('cover_url', 'null');

-- Entiers optionnels : valeurs limites acceptées, un cran au-delà refusé, null accepté.
select pg_temp.accepte_valeur(c, v)
from (values ('rating', '0'), ('rating', '5'), ('rating', 'null'),
             ('rawg_id', '0'), ('rawg_id', '9999999999'), ('rawg_id', 'null'),
             ('igdb_id', '1'), ('igdb_id', '9999999999'), ('igdb_id', 'null'),
             ('release_year', '1950'), ('release_year', '2999'), ('release_year', 'null'),
             ('estimated_playtime_hastily_hours', '0'), ('estimated_playtime_hastily_hours', '100000'),
             ('estimated_playtime_hastily_hours', 'null'),
             ('estimated_playtime_normally_hours', '0'), ('estimated_playtime_normally_hours', '100000'),
             ('estimated_playtime_normally_hours', 'null'),
             ('estimated_playtime_completely_hours', '0'), ('estimated_playtime_completely_hours', '100000'),
             ('estimated_playtime_completely_hours', 'null'),
             ('user_playtime_hours', '0'), ('user_playtime_hours', '100000'),
             ('completed_at', '0'), ('completed_at', '32503680000000'), ('completed_at', 'null'),
             ('abandoned_at', '0'), ('abandoned_at', '32503680000000'), ('abandoned_at', 'null'))
    as l (c, v);
select pg_temp.refuse_valeur(c, v)
from (values ('rating', '-1'), ('rating', '6'),
             ('rawg_id', '-1'), ('rawg_id', '10000000000'),
             ('igdb_id', '0'), ('igdb_id', '-1'), ('igdb_id', '10000000000'),
             ('release_year', '1949'), ('release_year', '3000'),
             ('estimated_playtime_hastily_hours', '-1'), ('estimated_playtime_hastily_hours', '100001'),
             ('estimated_playtime_normally_hours', '-1'), ('estimated_playtime_normally_hours', '100001'),
             ('estimated_playtime_completely_hours', '-1'), ('estimated_playtime_completely_hours', '100001'),
             ('user_playtime_hours', '-1'), ('user_playtime_hours', '100001'),
             ('completed_at', '-1'), ('completed_at', '32503680000001'),
             ('abandoned_at', '-1'), ('abandoned_at', '32503680000001'))
    as r (c, v);
-- Le temps de jeu saisi n'a pas de valeur nulle (le modèle est un Int non nul).
select pg_temp.refuse(pg_temp.requete_valeur('user_playtime_hours', 'null'), 'temps de jeu nul', '23502');

-- Plateformes jouées : vide, jusqu'à 20 éléments, pas 21.
select pg_temp.accepte_valeur('played_platforms', '''{}''');
select pg_temp.accepte_valeur('played_platforms', 'array_fill(''PC''::text, array[20])');
select pg_temp.refuse_valeur('played_platforms', 'array_fill(''PC''::text, array[21])');
select pg_temp.refuse(pg_temp.requete_valeur('played_platforms', 'null'), 'plateformes jouées nulles', '23502');

-- Les contraintes valent aussi à la modification.
select pg_temp.refuse($q$update cartouche.game set rating = 6$q$, 'note hors bornes en modification', '23514');
select pg_temp.refuse($q$update cartouche.game set status = 'VU'$q$, 'statut inconnu en modification', '23514');
commit;

-- `igdb_id` : absent d'une insertion, il vaut null ; un upsert qui ne l'envoie pas (celui des
-- versions de l'application et du site antérieures à la colonne) ne l'efface pas ; un upsert qui
-- l'envoie le met à jour.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game (id, title, platform, genre, status)
    values ('22222222-2222-2222-2222-222222222222', 'Jeu IGDB', 'PC', 'RPG', 'A_FAIRE');
do $$ begin
    assert (select igdb_id from cartouche.game where id = '22222222-2222-2222-2222-222222222222') is null,
        'igdb_id non nul sans avoir été envoyé';
end $$;
update cartouche.game set igdb_id = 1029 where id = '22222222-2222-2222-2222-222222222222';
insert into cartouche.game (id, title, platform, genre, status)
    values ('22222222-2222-2222-2222-222222222222', 'Jeu IGDB', 'PC', 'RPG', 'EN_COURS')
    on conflict (id) do update set status = excluded.status;
do $$ begin
    assert (select igdb_id from cartouche.game where id = '22222222-2222-2222-2222-222222222222') = 1029,
        'un upsert sans igdb_id a effacé igdb_id';
    assert (select status from cartouche.game where id = '22222222-2222-2222-2222-222222222222') = 'EN_COURS',
        'upsert partiel : statut non mis à jour';
end $$;
insert into cartouche.game (id, title, platform, genre, status, igdb_id)
    values ('22222222-2222-2222-2222-222222222222', 'Jeu IGDB', 'PC', 'RPG', 'EN_COURS', 405460)
    on conflict (id) do update set igdb_id = excluded.igdb_id;
do $$ begin
    assert (select igdb_id from cartouche.game where id = '22222222-2222-2222-2222-222222222222') = 405460,
        'un upsert avec igdb_id ne l''a pas mis à jour';
end $$;
delete from cartouche.game where id = '22222222-2222-2222-2222-222222222222';
commit;

-- Suppression : A retire son jeu.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
delete from cartouche.game where id = '11111111-1111-1111-1111-111111111111';
do $$ begin
    assert (select count(*) from cartouche.game where id = '11111111-1111-1111-1111-111111111111') = 0,
        'le jeu supprimé est toujours là';
end $$;
commit;

-- Sans compte connecté, rien n'est lisible ni écrivable.
begin;
set local role anon;
select pg_temp.refuse('select * from cartouche.game', 'lecture anonyme', '42501');
select pg_temp.refuse($q$insert into cartouche.game (id, user_id, title, platform, genre, status) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'A_FAIRE')$q$, 'insertion anonyme', '42501');
commit;

-- Même si le schéma était un jour ouvert à `anon`, la table lui reste fermée (annulé ensuite).
begin;
grant usage on schema cartouche to anon;
set local role anon;
select pg_temp.refuse('select * from cartouche.game', 'lecture anonyme, schéma ouvert', '42501');
select pg_temp.refuse($q$insert into cartouche.game (id, user_id, title, platform, genre, status) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 'x', 'x', 'x', 'A_FAIRE')$q$, 'insertion anonyme, schéma ouvert', '42501');
rollback;

-- Un compte connecté ne peut pas créer d'objet dans le schéma, ni y toucher à la structure.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('create table cartouche.intrus (id int)', 'création de table', '42501');
select pg_temp.refuse('truncate cartouche.game', 'truncate par un compte', '42501');
commit;

-- Le schéma `public` (propriété de Pellicule) reste tel quel : le compte y garde son seul droit.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('insert into public.temoin values (1)', 'écriture dans public', '42501');
commit;

-- Droits exacts sur la table, quels que soient les privilèges par défaut du projet : les quatre
-- opérations de l'API pour `authenticated` (ni `truncate`, ni `references`, ni `trigger`), rien
-- pour `anon` ni pour tout le monde.
do $$ begin
    assert (select string_agg(privilege_type, ',' order by privilege_type)
            from information_schema.table_privileges
            where table_schema = 'cartouche' and table_name = 'game' and grantee = 'authenticated')
        = 'DELETE,INSERT,SELECT,UPDATE', 'droits de authenticated sur cartouche.game non minimaux';
    assert not exists (select 1 from information_schema.table_privileges
                       where table_schema = 'cartouche' and table_name = 'game'
                         and grantee in ('anon', 'PUBLIC')),
        'anon ou PUBLIC a un droit sur cartouche.game';
end $$;

-- Descriptions (`cartouche.game_description`) : isolation entre comptes, jeu appartenant au même
-- compte, contraintes CHECK, cascade, droits.
-- Jeux de départ : deux pour A (`...a1`, `...a2`), un pour B (`...b1`).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game (id, title, platform, genre, status) values
    ('a1a1a1a1-0000-4000-8000-0000000000a1', 'Jeu A1', 'PC', 'RPG', 'A_FAIRE'),
    ('a2a2a2a2-0000-4000-8000-0000000000a2', 'Jeu A2', 'PC', 'RPG', 'A_FAIRE');
commit;
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
insert into cartouche.game (id, title, platform, genre, status) values
    ('b1b1b1b1-0000-4000-8000-0000000000b1', 'Jeu B1', 'PC', 'RPG', 'A_FAIRE');
commit;

-- A décrit son jeu : user_id et created_at prennent leur valeur par défaut, les textes restent
-- tels quels (accents, retours à la ligne).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game_description (game_id, igdb_id, summary, summary_fr, translation_model)
    values ('a1a1a1a1-0000-4000-8000-0000000000a1', 1029, E'An epic quest.\nSecond line.',
            E'Une quête épique.\nDeuxième ligne.', 'modele-test');
do $$ begin
    assert (select user_id from cartouche.game_description) = 'aaaaaaaa-0000-0000-0000-000000000001',
        'description : user_id par défaut';
    assert (select created_at from cartouche.game_description) is not null, 'description : created_at par défaut';
    assert (select summary_fr from cartouche.game_description) = E'Une quête épique.\nDeuxième ligne.',
        'description : texte altéré';
end $$;
-- Une description par jeu : une seconde insertion pour le même jeu est un doublon.
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, igdb_id, summary)
       values ('a1a1a1a1-0000-4000-8000-0000000000a1', 1029, 'doublon')$q$,
    'deux descriptions pour le même jeu', '23505');
-- A ne peut pas décrire un jeu qui n'existe pas, ni au nom d'un autre compte.
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, igdb_id, summary)
       values (gen_random_uuid(), 1029, 'jeu inconnu')$q$,
    'description d''un jeu inexistant', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, user_id, igdb_id, summary)
       values ('a2a2a2a2-0000-4000-8000-0000000000a2', 'bbbbbbbb-0000-0000-0000-000000000002', 1029, 'x')$q$,
    'insertion au nom de B', '42501');
-- A ne peut pas décrire le jeu de B.
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, igdb_id, summary)
       values ('b1b1b1b1-0000-4000-8000-0000000000b1', 1029, 'sur le jeu de B')$q$,
    'description du jeu d''un autre compte', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, user_id, igdb_id, summary)
       values ('b1b1b1b1-0000-4000-8000-0000000000b1', 'aaaaaaaa-0000-0000-0000-000000000001', 1029, 'x')$q$,
    'description du jeu de B en son nom', '42501');
commit;

-- B décrit son jeu, ne voit rien de A, ne modifie rien, ne supprime rien.
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
insert into cartouche.game_description (game_id, igdb_id, summary)
    values ('b1b1b1b1-0000-4000-8000-0000000000b1', 2048, 'Texte de B');
do $$ begin
    assert (select count(*) from cartouche.game_description) = 1, 'B voit la description de A';
    assert (select summary from cartouche.game_description) = 'Texte de B', 'B ne voit pas sa description';
end $$;
update cartouche.game_description set summary = 'piraté' where game_id = 'a1a1a1a1-0000-4000-8000-0000000000a1';
delete from cartouche.game_description where game_id = 'a1a1a1a1-0000-4000-8000-0000000000a1';
-- Une modification sans filtre ne touche que la ligne de B : sous une politique de modification
-- trop large, elle atteindrait celle de A (et serait refusée sur elle). Recomptée par A plus bas.
update cartouche.game_description set translation_model = 'modele-b';
do $$ begin
    assert (select translation_model from cartouche.game_description) = 'modele-b',
        'B ne peut pas modifier sa propre description';
end $$;
-- B ne peut pas céder sa ligne à A, ni la déplacer sur le jeu de A.
select pg_temp.refuse(
    $q$update cartouche.game_description set user_id = 'aaaaaaaa-0000-0000-0000-000000000001'$q$,
    'changement de propriétaire de la description', '42501');
select pg_temp.refuse(
    $q$update cartouche.game_description set game_id = 'a2a2a2a2-0000-4000-8000-0000000000a2'$q$,
    'déplacement de la description vers un jeu de A sans description', '42501');
select pg_temp.refuse(
    $q$insert into cartouche.game_description (game_id, igdb_id, summary)
       values ('a1a1a1a1-0000-4000-8000-0000000000a1', 1029, 'upsert')
       on conflict (game_id) do update set summary = excluded.summary$q$,
    'upsert sur la description de A', '42501');
commit;

-- Rien n'a bougé pour A ; A remplace une description périmée (une ligne touchée) et peut en
-- enregistrer une sans texte (IGDB n'en a pas).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
do $$ begin
    assert (select count(*) from cartouche.game_description) = 1, 'les descriptions de A ont changé';
    assert (select summary from cartouche.game_description) = E'An epic quest.\nSecond line.',
        'la description de A a été modifiée par B';
    assert (select translation_model from cartouche.game_description) = 'modele-test',
        'la modification sans filtre de B a atteint la description de A';
end $$;
update cartouche.game_description set igdb_id = 405460, summary = 'New text', summary_fr = 'Nouveau texte',
    translation_model = 'modele-test-2' where game_id = 'a1a1a1a1-0000-4000-8000-0000000000a1';
do $$ begin
    assert (select igdb_id from cartouche.game_description) = 405460, 'description : mise à jour non faite';
end $$;
insert into cartouche.game_description (game_id, igdb_id, summary, summary_fr)
    values ('a2a2a2a2-0000-4000-8000-0000000000a2', 77, null, null);
do $$ begin
    assert (select count(*) from cartouche.game_description where summary is null) = 1, 'description sans texte non enregistrée';
end $$;
commit;

-- Contraintes CHECK : bornes de longueur, identifiant IGDB, cohérence original / traduction.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
insert into cartouche.game (id, title, platform, genre, status)
    values ('a3a3a3a3-0000-4000-8000-0000000000a3', 'Jeu A3', 'PC', 'RPG', 'A_FAIRE');
create function pg_temp.requete_description(colonne text, valeur text) returns text
language sql as $$
    select format(
        'insert into cartouche.game_description (game_id, igdb_id, summary, %I) '
        'values (''a3a3a3a3-0000-4000-8000-0000000000a3'', 1, ''x'', %s)', colonne, valeur)
$$;
-- Une insertion acceptée est annulée aussitôt (sous-transaction) pour rester sur le même jeu.
create function pg_temp.accepte_description(colonne text, valeur text) returns void
language plpgsql as $$
begin
    begin
        execute pg_temp.requete_description(colonne, valeur);
        raise exception 'annule';
    exception when raise_exception then
        if sqlerrm <> 'annule' then raise; end if;
    end;
end;
$$;
create function pg_temp.refuse_description(colonne text, valeur text) returns void
language plpgsql as $$
begin
    perform pg_temp.refuse(pg_temp.requete_description(colonne, valeur), colonne || ' = ' || valeur, '23514');
end;
$$;
select pg_temp.accepte_description('summary_fr', 'repeat(''é'', 10000)');
select pg_temp.refuse_description('summary_fr', 'repeat(''é'', 10001)');
select pg_temp.accepte_description('translation_model', 'repeat(''x'', 100)');
select pg_temp.refuse_description('translation_model', 'repeat(''x'', 101)');
select pg_temp.accepte_description('translation_model', 'null');
select pg_temp.refuse('insert into cartouche.game_description (game_id, igdb_id, summary) values (''a3a3a3a3-0000-4000-8000-0000000000a3'', 1, repeat(''x'', 5001))',
    'original de 5001 caractères', '23514');
select pg_temp.refuse('insert into cartouche.game_description (game_id, igdb_id, summary) values (''a3a3a3a3-0000-4000-8000-0000000000a3'', 1, repeat(''x'', 5000)), (''a3a3a3a3-0000-4000-8000-0000000000a3'', 1, ''x'')',
    'doublon dans une même requête', '23505');
-- Identifiant IGDB : de 1 à 9999999999, obligatoire.
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, igdb_id) values ('a3a3a3a3-0000-4000-8000-0000000000a3', 0)$q$, 'igdb_id = 0', '23514');
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, igdb_id) values ('a3a3a3a3-0000-4000-8000-0000000000a3', -1)$q$, 'igdb_id = -1', '23514');
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, igdb_id) values ('a3a3a3a3-0000-4000-8000-0000000000a3', 10000000000)$q$, 'igdb_id = 10000000000', '23514');
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, igdb_id) values ('a3a3a3a3-0000-4000-8000-0000000000a3', null)$q$, 'igdb_id nul', '23502');
select pg_temp.refuse($q$insert into cartouche.game_description (game_id) values ('a3a3a3a3-0000-4000-8000-0000000000a3')$q$, 'igdb_id absent', '23502');
-- Une traduction sans original n'a pas de sens.
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, igdb_id, summary, summary_fr) values ('a3a3a3a3-0000-4000-8000-0000000000a3', 1, null, 'seule')$q$, 'traduction sans original', '23514');
select pg_temp.refuse($q$update cartouche.game_description set summary = null where game_id = 'a1a1a1a1-0000-4000-8000-0000000000a1'$q$, 'original retiré en gardant la traduction', '23514');
select pg_temp.refuse($q$update cartouche.game_description set igdb_id = 0 where game_id = 'a1a1a1a1-0000-4000-8000-0000000000a1'$q$, 'igdb_id hors bornes en modification', '23514');
rollback;

-- La description suit son jeu : supprimer le jeu supprime la description (la cascade ignore le
-- RLS, mais seul le propriétaire peut supprimer son jeu). Les autres descriptions restent.
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
delete from cartouche.game where id = 'a1a1a1a1-0000-4000-8000-0000000000a1';
do $$ begin
    assert (select count(*) from cartouche.game_description) = 1, 'cascade : la description du jeu supprimé reste';
    assert (select game_id from cartouche.game_description) = 'a2a2a2a2-0000-4000-8000-0000000000a2',
        'cascade : mauvaise description supprimée';
end $$;
commit;
-- La suppression d'un jeu par A n'a pas touché la description de B.
begin;
select pg_temp.connecte_comme('bbbbbbbb-0000-0000-0000-000000000002');
do $$ begin
    assert (select count(*) from cartouche.game_description) = 1, 'cascade : description de B touchée';
end $$;
-- B supprime lui-même sa description (le jeu reste).
delete from cartouche.game_description;
do $$ begin
    assert (select count(*) from cartouche.game_description) = 0, 'suppression de sa description refusée';
    assert (select count(*) from cartouche.game) = 1, 'le jeu de B a disparu avec sa description';
end $$;
commit;
-- La suppression sans filtre de B n'a emporté aucune description de A (sous une politique de
-- suppression trop large, elle aurait vidé la table).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
do $$ begin
    assert (select count(*) from cartouche.game_description
            where game_id = 'a2a2a2a2-0000-4000-8000-0000000000a2') = 1,
        'la suppression sans filtre de B a supprimé la description de A';
end $$;
commit;

-- Sans compte connecté, rien n'est lisible ni écrivable, même schéma ouvert à `anon`.
begin;
set local role anon;
select pg_temp.refuse('select * from cartouche.game_description', 'lecture anonyme des descriptions', '42501');
select pg_temp.refuse($q$insert into cartouche.game_description (game_id, user_id, igdb_id) values (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', 1)$q$, 'insertion anonyme d''une description', '42501');
commit;
begin;
grant usage on schema cartouche to anon;
set local role anon;
select pg_temp.refuse('select * from cartouche.game_description', 'lecture anonyme des descriptions, schéma ouvert', '42501');
rollback;

-- Un compte connecté ne peut pas vider la table (`truncate` ignore le RLS).
begin;
select pg_temp.connecte_comme('aaaaaaaa-0000-0000-0000-000000000001');
select pg_temp.refuse('truncate cartouche.game_description', 'truncate des descriptions par un compte', '42501');
commit;

-- Droits exacts : les quatre opérations de l'API pour `authenticated`, rien pour `anon` ni
-- pour tout le monde.
do $$ begin
    assert (select string_agg(privilege_type, ',' order by privilege_type)
            from information_schema.table_privileges
            where table_schema = 'cartouche' and table_name = 'game_description' and grantee = 'authenticated')
        = 'DELETE,INSERT,SELECT,UPDATE', 'droits de authenticated sur cartouche.game_description non minimaux';
    assert not exists (select 1 from information_schema.table_privileges
                       where table_schema = 'cartouche' and table_name = 'game_description'
                         and grantee in ('anon', 'PUBLIC')),
        'anon ou PUBLIC a un droit sur cartouche.game_description';
    -- Les droits de `cartouche.game` n'ont pas bougé.
    assert (select string_agg(privilege_type, ',' order by privilege_type)
            from information_schema.table_privileges
            where table_schema = 'cartouche' and table_name = 'game' and grantee = 'authenticated')
        = 'DELETE,INSERT,SELECT,UPDATE', 'droits de authenticated sur cartouche.game modifiés';
    -- RLS active, et quatre politiques exactement (une par opération).
    assert (select relrowsecurity from pg_class where oid = 'cartouche.game_description'::regclass),
        'RLS inactive sur cartouche.game_description';
    assert (select count(*) from pg_policies
            where schemaname = 'cartouche' and tablename = 'game_description') = 4,
        'nombre de politiques inattendu sur cartouche.game_description';
end $$;

-- Realtime : seule `cartouche.game` rejoint la publication.
do $$ begin
    assert (select count(*) from pg_publication_tables where pubname = 'supabase_realtime') = 1,
        'la publication Realtime contient autre chose que cartouche.game';
    assert exists (select 1 from pg_publication_tables
                   where pubname = 'supabase_realtime' and schemaname = 'cartouche' and tablename = 'game'),
        'cartouche.game absente de la publication Realtime';
end $$;

select 'OK : tous les tests RLS passent' as resultat;
