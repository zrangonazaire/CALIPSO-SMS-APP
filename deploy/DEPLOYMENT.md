# Déploiement Calipso sur le VPS

Cible : `ubuntu@149.56.209.224`, domaine `calipso-sms.pro`. Base de production neuve, sans copie des données locales. Aucun SMS ni e-mail n'est envoyé par la procédure de validation.

## Préparation

1. Autoriser la clé publique de déploiement pour le compte `ubuntu`. Ne jamais transmettre la clé privée.
2. Exécuter `bash deploy/preflight.sh` sur le serveur et vérifier les services existants avant toute installation.
3. Installer Docker Engine et Compose depuis [le dépôt officiel pour Ubuntu](https://docs.docker.com/engine/install/ubuntu/) si nécessaire. Ne pas désinstaller une version existante sans examiner ses conteneurs.
4. Transférer les sources de la livraison dans `/opt/calipso`. Les fichiers `*.local.properties`, les secrets, les sauvegardes et les dépendances locales sont exclus de l'archive.
5. Copier `.env.production.example` vers `.env.production`, renseigner le domaine et l'adresse de l'administrateur. Si aucune boîte mail n'est disponible, utiliser provisoirement `admin@calipso.invalid`. Cette adresse ne permet pas une récupération du mot de passe par e-mail.
6. Exécuter `bash deploy/init-secrets.sh`. Cette commande crée les secrets manquants et préserve ceux déjà présents. Le nouveau mot de passe initial se trouve dans `deploy/.secrets/bootstrap_password` ; le consulter uniquement depuis une session privée. Modifier le mot de passe dans l'application après la première connexion. Le bootstrap ne réinitialise pas un compte déjà créé.

L'inscription publique est activée dans Docker avec `CALIPSO_PUBLIC_REGISTRATION_ENABLED=true`. Le bouton « Créer un compte gratuit » apparaît sur la page de connexion. Chaque inscription crée une entreprise et son administrateur, sans droits d'administration de plateforme. Pour fermer les inscriptions, définir cette variable à `false` et recréer le backend. Le profil `prod` utilisé sans cette configuration Docker reste fermé par défaut.

## DNS, HTTPS et réseau

Configurer l'enregistrement DNS A de `calipso-sms.pro` avec `149.56.209.224`. Retirer un éventuel AAAA qui pointerait vers un autre serveur. Le sous-domaine `www` n'est pas nécessaire pour ce déploiement.

Sur un VPS vide, autoriser TCP 80/443 et le port SSH courant dans le pare-feu de l'hébergeur et du système. UDP 443 permet HTTP/3, sans être indispensable. Conserver l'accès SSH avant toute modification du pare-feu. N'exposer ni PostgreSQL ni le backend.

Exécuter `bash deploy/up.sh edge`. Caddy obtient et renouvelle les certificats lorsque le DNS pointe vers le serveur et que les ports sont accessibles. Les certificats sont conservés dans un volume dédié. Voir [les prérequis HTTPS de Caddy](https://caddyserver.com/docs/automatic-https).

Si un autre proxy existe : utiliser `bash deploy/up.sh behind-proxy` et configurer le proxy HTTPS existant vers `127.0.0.1:18080`. Ce mode ne lance pas Caddy et n'occupe pas 80/443. Le proxy doit remplacer les en-têtes transmis par le client, notamment `X-Forwarded-For` et `X-Forwarded-Proto`, et appliquer les en-têtes de sécurité du Caddyfile.

## Vérifications

- Vérifier `docker compose --env-file .env.production -f compose.production.yml --profile edge ps` : base, backend et frontend doivent être sains.
- Vérifier la redirection HTTP vers HTTPS et le certificat public, sans désactiver sa validation.
- Vérifier la connexion de l'administrateur et son accès à l'administration des entreprises.
- Vérifier que l'inscription publique respecte le réglage configuré, qu'un accès non authentifié aux entreprises est refusé, que Swagger et Actuator ne sont pas accessibles publiquement, et que les réponses API ne contiennent aucun secret. Avec les inscriptions ouvertes, lancer `deploy/smoke-test.py` avec l'option `--public-registration` ; ce contrôle ne crée aucun compte.
- Configurer ensuite les identifiants Orange, WhatsApp et SMTP propres à chaque entreprise. Les envois non configurés sont refusés en production et les paiements simulés sont désactivés.

## Sauvegarde et mises à jour

`bash deploy/backup.sh` produit un dump PostgreSQL au format custom avec SHA-256, en accès privé. Programmer cette commande quotidiennement et conserver une copie chiffrée hors du VPS. Sauvegarder séparément les secrets, surtout `encryption_key` : ils sont indispensables pour déchiffrer les intégrations après une restauration.

Pour une installation dans `/opt/calipso`, les unités `calipso-backup.service` et `calipso-backup.timer` peuvent être copiées dans `/etc/systemd/system/`, puis activées avec `sudo systemctl daemon-reload` et `sudo systemctl enable --now calipso-backup.timer`. Le déclenchement est quotidien à 03:15 UTC, avec rattrapage si le serveur était arrêté. Les sauvegardes restent locales tant qu'une copie externe n'est pas configurée.

`deploy/up.sh` sauvegarde une base déjà en cours d'exécution avant une mise à jour. Le schéma utilise encore Hibernate `ddl-auto=update` : contrôler les changements de schéma avant chaque livraison. Conserver les anciennes images et un dump vérifié pour un retour arrière ; un retour à une ancienne image seul ne restaure pas le schéma.

Pour contrôler une sauvegarde : `pg_restore --list sauvegarde.dump`. Une restauration effective doit être testée dans une base isolée, jamais directement sur la production active.

Ne jamais utiliser `docker compose down -v`, `docker system prune` ou supprimer des volumes pendant un déploiement.

## Livraisons reproductibles

Créer une archive avec `python deploy/package-release.py`. Elle contient les sources actuelles, y compris les changements non commités nécessaires à cette livraison, et exclut les secrets et fichiers locaux. Son empreinte SHA-256 est enregistrée à côté de l'archive. Utiliser un tag `CALIPSO_RELEASE` unique par livraison et conserver l'archive correspondante.

`CALIPSO_FRONTEND_RELEASE` permet une livraison du frontend sans modifier la version du backend. Si cette variable est vide, le frontend utilise `CALIPSO_RELEASE`.

L'optimisation Angular de production conserve `inlineCritical=false` pour les styles. Le chargement différé des styles via un gestionnaire `onload` intégré est bloqué par la politique CSP ; utiliser des liens de feuilles de style ordinaires conserve leur affichage avec `script-src 'self'`. Vérifier les styles calculés des champs et l'absence de violations CSP dans un navigateur après chaque livraison.

Si les images correspondant exactement à cette livraison ont été construites et testées ailleurs, les transférer avec `docker save`, les charger avec `docker load`, puis lancer `CALIPSO_SKIP_BUILD=1 bash deploy/up.sh edge` pour utiliser les images validées. Vérifier leurs empreintes avant le chargement.
