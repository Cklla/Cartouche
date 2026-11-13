-- Identifiant IGDB d'un jeu : colonne `igdb_id` de `cartouche.game`.
--
-- Un jeu ajouté depuis IGDB connaît son identifiant : ses temps estimés, ses plateformes et sa
-- jaquette se lisent alors directement par identifiant, sans correspondance floue sur le titre.
-- `rawg_id` est conservé tel quel (aucune donnée supprimée).
--
-- Cette migration n'ajoute que la colonne et sa contrainte : ni politique RLS, ni colonne
-- existante, ni schéma `public`. Aucun `grant` : ceux de la migration initiale portent sur la
-- table entière (`select, insert, update, delete` pour `authenticated`) et couvrent donc déjà la
-- nouvelle colonne ; `anon` n'a toujours aucun accès.
--
-- Sûre pour les versions déjà installées de l'application et du site :
--   - la colonne est facultative et sans valeur par défaut (`null` pour les jeux existants) ;
--   - un client qui ne la connaît pas ne l'envoie pas, et ses écritures la laissent intacte
--     (un upsert PostgREST ne met à jour que les colonnes présentes dans le corps de la requête) ;
--   - un client qui ne la connaît pas ignore la colonne en lecture.
--
-- Borne : de 1 à 9999999999, comme `rawg_id` mais sans le 0 (un identifiant IGDB vaut au moins 1).
-- `null` reste accepté : un contrôle `check` ne s'applique pas à une valeur nulle.
--
-- À appliquer une seule fois, à la main, dans l'éditeur SQL du tableau de bord, après une
-- sauvegarde. Un rejeu échoue (la colonne existe déjà) sans rien modifier.
--
-- Retour arrière (à lancer à la main ; efface les identifiants IGDB déjà enregistrés, qui sont
-- ensuite retrouvés par la remise à niveau) :
--   alter table cartouche.game drop column igdb_id;

alter table cartouche.game
    add column igdb_id bigint check (igdb_id between 1 and 9999999999);
