-- Scénario pessimiste, joué seulement par la seconde passe de lancer.sh : le projet accorde
-- tout par défaut aux rôles de l'API sur les nouveaux schémas et les nouvelles tables. Les
-- migrations doivent alors retirer ce qui est en trop (droits minimaux, rien pour `anon`).
--
-- La première passe, sans ce fichier, prouve l'inverse : aucun GRANT nécessaire n'est oublié.

alter default privileges for role postgres grant usage on schemas to anon, authenticated;
alter default privileges for role postgres grant all on tables to anon, authenticated;
