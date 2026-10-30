# CALIPSO-SMS-APP

Les modèles d’en-têtes sont accessibles dans **Studio & Suivi → En-têtes SMS / Excel**.
Ils appartiennent à l’entreprise active et peuvent être créés depuis un fichier
`.xlsx`, `.xls` ou `.csv`, y compris un fichier contenant uniquement les en-têtes,
ou en ajoutant les colonnes individuellement. Après import, choisissez les colonnes
à conserver, leurs codes de variables, leurs types et les champs obligatoires.
Pour les SMS et WhatsApp, désignez une seule colonne destinataire SMS.

Enregistrez le modèle pour le modifier, dupliquer sa structure, l’archiver ou
télécharger un Excel vide. Des messages réutilisables peuvent lui être associés.
Dans une nouvelle campagne, sélectionnez le modèle à l’étape Contacts & Fichier,
importez les destinataires et vérifiez les correspondances proposées. Les noms
de colonnes peuvent être associés manuellement ; leur ordre n’a pas d’importance.
Les champs obligatoires, les types et les variables du message sont contrôlés
dans l’interface et sur le serveur. Les dates acceptées sont `AAAA-MM-JJ` et
`JJ/MM/AAAA` ; les nombres peuvent contenir des espaces et une virgule décimale.

Chaque campagne conserve les définitions des colonnes, leurs correspondances et
la position des en-têtes. Réimporter un fichier dans une campagne non envoyée
réutilise cette copie, même si le modèle a ensuite été modifié ou archivé.
Les destinataires des campagnes envoyées ne peuvent plus être remplacés.

Le schéma ajoute `header_name` et `column_order` à `excel_variables`, et
`import_configuration` (JSONB) à `campaigns`. La configuration locale utilise
déjà `spring.jpa.hibernate.ddl-auto=update` : ces champs sont créés au prochain
démarrage du backend. Les anciens profils restent utilisables ; le nom d’en-tête
utilise leur code existant lorsqu’aucun nom distinct n’a été enregistré.

Vérifications ciblées (PowerShell, depuis chaque sous-projet) :

```powershell
# calipso-backend
mvn.cmd '-Dtest=ExcelTableReaderTest,HeaderModelServiceTest,CampaignHeaderServiceTest,ExcelImportServiceTest' test

# calipso-frontend
npm.cmd test -- --watch=false --include='**/header-model.spec.ts' --include='**/import-profile-list.spec.ts' --include='**/campaign-create.spec.ts'
npm.cmd run build
```

Les accès sont isolés par entreprise côté serveur. Le rôle `ADMIN` désigne un
administrateur de son entreprise ; il ne donne aucun accès aux autres entreprises.
Les comptes existants gardent leur rôle, mais leurs requêtes sont désormais
limitées à leur entreprise. Une requête visant les données d’une autre entreprise
renvoie HTTP 403, y compris pour un identifiant de campagne, profil, variable ou membre.

L’accès d’administration de plateforme nécessite **à la fois** le rôle `ADMIN`
et l’identifiant persistant d’un compte existant dans la variable serveur
`CALIPSO_PLATFORM_ADMIN_USER_IDS` (identifiants séparés par des virgules).
Cette liste est vide par défaut. Aucun compte n’est promu automatiquement,
y compris le premier compte créé ou un compte inscrit via `/auth/setup`. Le seul
amorçage automatique est explicite : configurez
`CALIPSO_SECURITY_BOOTSTRAP_USERNAME` avec `CALIPSO_SECURITY_BOOTSTRAP_PASSWORD`
et la même adresse dans `CALIPSO_SECURITY_BOOTSTRAP_EMAIL`. À chaque démarrage,
un compte existant n’est autorisé que si son rôle et son adresse e-mail
correspondent à cette configuration. Vérifiez soigneusement les valeurs; la
liste d’autorisations ne peut pas être modifiée depuis les membres d’une entreprise.
Le serveur renvoie le droit effectif dans `platformAdmin` à la connexion et via
`/auth/me`. Le sélecteur multi-entreprises et l’administration de plateforme
sont affichés uniquement pour ces comptes autorisés.

Les notifications de paiement qui modifient des transactions doivent fournir
le secret serveur `CALIPSO_PAYMENT_WEBHOOK_SECRET` dans l’en-tête
`X-Calipso-Webhook-Secret`, ou être envoyées par un administrateur de plateforme
authentifié. Sans secret configuré, les appels anonymes sont refusés.
Les accusés de réception Orange et les informations des contrats Orange partagés
sont réservés à l’administration de plateforme. Les tableaux de bord d’entreprise
conservent leur historique et leur solde propres.

Les codes de récupération de mot de passe ne sont plus renvoyés dans la réponse
publique ni écrits dans les journaux. Un administrateur d’entreprise ne peut pas
réinitialiser le mot de passe d’un compte d’administration de plateforme.

Tests d’isolation (depuis `calipso-backend`) :

```powershell
mvn.cmd '-Dtest=CompanyIsolationHttpTest,PlatformPrivilegesTest' test
```
# Administration plateforme

Le compte superadministrateur est créé une seule fois au démarrage lorsque
`CALIPSO_SECURITY_BOOTSTRAP_USERNAME` est défini. Pour initialiser le compte
`admin`, injectez les variables suivantes dans l’environnement du backend ou
dans le gestionnaire de secrets de votre hébergeur :

```text
CALIPSO_SECURITY_BOOTSTRAP_USERNAME=admin
CALIPSO_SECURITY_BOOTSTRAP_PASSWORD=<mot de passe fourni, stocké comme secret>
CALIPSO_SECURITY_BOOTSTRAP_EMAIL=<adresse e-mail d’administration>
```

Le mot de passe n’est pas livré avec le code et est stocké en base sous forme
hachée BCrypt. Ne placez pas sa valeur dans un fichier versionné. Après le
premier démarrage, le compte reste administrateur plateforme; retirez le mot
de passe du secret d’amorçage si votre processus de déploiement le permet,
mais conservez le nom d’utilisateur pour que l’autorisation de plateforme
soit restaurée après un redémarrage.

Les identifiants Orange, WhatsApp Cloud et SMTP configurés par entreprise sont
chiffrés AES-GCM en base. Avant de les enregistrer, définissez
`CALIPSO_INTEGRATIONS_ENCRYPTION_KEY` avec une clé aléatoire Base64 de 32
octets (par exemple générée par `openssl rand -base64 32`) dans le gestionnaire
de secrets du backend. Cette clé doit rester stable et être sauvegardée de
manière sécurisée : les secrets déjà stockés ne pourront pas être déchiffrés
avec une autre clé. L’écran « Intégrations entreprise » et la synthèse
multi-entreprises sont réservés au superadministrateur.

## Orange SMS Côte d’Ivoire

La configuration par entreprise se trouve dans **Configuration → Intégrations
entreprises**, avec un compte superadministrateur. Sélectionnez l’entreprise,
puis saisissez ses Client ID et Client Secret issus de l’application Orange
Developer abonnée à **SMS Côte d’Ivoire**. Un bundle Orange valide et crédité est
nécessaire, en plus des crédits internes Calipso.

L’adresse technique est `tel:+2250000`, encodée `tel%3A%2B2250000` dans l’URL.
Le nom visible (par exemple `MAGISER`) se configure séparément dans **Nom
d’expéditeur** : 11 caractères alphanumériques ou espaces maximum, préalablement
approuvés par Orange. Sans nom personnalisé, Orange utilise celui du compte.
Les destinataires sont des numéros ivoiriens à dix chiffres, préfixés par `+225`.

L’authentification utilise OAuth 2.0 client credentials sur
`https://api.orange.com/oauth/v3/token`, puis l’envoi utilise Bearer sur
`https://api.orange.com/smsmessaging/v1/outbound/tel%3A%2B2250000/requests`.
Les jetons des comptes entreprise sont réutilisés jusqu’à expiration et renouvelés
après HTTP 401. HTTP 201 signifie accepté par Orange, pas livré au téléphone.
Un échec Orange reste un échec : aucun succès de livraison n’est simulé.
Les accusés réels nécessitent une URL HTTPS publique sur le port 443, enregistrée
auprès d’Orange ; la configuration locale seule ne les active pas.

Référence officielle : https://developer.orange.com/apis/sms/getting-started
