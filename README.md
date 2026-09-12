# Reroutage SMS

Application Android minimale qui transfere automatiquement les SMS recus depuis
les contacts d'un groupe donne vers un numero unique.

Cible de developpement : Galaxy Fold 5, Android 15 (One UI 8.5).
minSdk 26, targetSdk 36.

## Principe

1. Un SMS arrive.
2. L'application compare le numero de l'expediteur aux numeros des contacts
   membres du groupe surveille (par defaut `Reroutage`).
3. Si l'expediteur en fait partie, le contenu est renvoye par SMS au numero de
   destination configure.

Le recepteur est declare dans le manifeste : il fonctionne meme quand
l'application est fermee. Il est automatiquement desactive lorsque le reroutage
est coupe, pour que le systeme cesse de reveiller l'application a chaque SMS.

## Installation sur le telephone

1. Copier `ReroutageSMS-1.0-debug.apk` sur le telephone (cable USB, Quick Share,
   Google Drive, etc.).
2. Ouvrir le fichier depuis l'explorateur de fichiers.
3. Autoriser l'installation depuis cette source lorsque One UI le demande.

Installation par cable, en alternative :

```bash
adb install -r ReroutageSMS-1.0-debug.apk
```

## Configuration

### 1. Creer le groupe de contacts

Dans l'application Contacts de Samsung : `Groupes` > `Creer un groupe`, puis y
ajouter les correspondants a surveiller.

Un membre du groupe peut etre un numero classique, un numero court, ou un
expediteur alphanumerique de service enregistre comme contact.

### 2. Regler l'application

- Accorder les permissions demandees : reception de SMS, envoi de SMS, contacts,
  notifications. Sans l'acces aux contacts, la liste des groupes reste vide.
- Choisir le numero de destination, soit en le saisissant, soit via l'icone de
  contact a droite du champ qui ouvre le repertoire.
- Choisir le groupe surveille dans la liste deroulante. Le texte sous le champ
  indique aussitot le nombre d'expediteurs effectivement surveilles : il doit
  correspondre au contenu du groupe.
- Activer `Reroutage actif`, puis `Enregistrer`.

Les groupes automatiques du repertoire (`Mes contacts`, `Favoris`) sont
volontairement absents de la liste : les selectionner rerouterait un volume
imprevisible de messages.

### 3. Reglages Samsung indispensables

Sans ces reglages, One UI peut mettre l'application en veille et les SMS ne
seront plus transferes.

- `Parametres` > `Batterie` > `Limites d'utilisation en arriere-plan` :
  verifier que l'application n'est pas dans `Applications en veille` ni dans
  `Applications en veille profonde`.
- `Parametres` > `Batterie` > `Optimisation batterie` : passer `Reroutage SMS`
  sur `Non optimise`. Le bouton `Exclure des optimisations de batterie` de
  l'application ouvre directement cet ecran.
- `Parametres` > `Applications` > `Reroutage SMS` > `Batterie` : choisir
  `Illimite`.

## Verification

1. Faire envoyer un SMS par un membre du groupe.
2. Le numero de destination doit recevoir le message.
3. Le journal en bas de l'application retrace le traitement.

### Lire le journal

L'envoi d'un SMS est asynchrone : la remise a la couche telephonie et la
confirmation de l'operateur sont deux evenements distincts, et le journal les
separe volontairement.

| Ligne | Signification |
| --- | --- |
| `Remis pour envoi depuis ...` | Le message a ete confie au systeme |
| `Envoi confirme par l'operateur` | L'operateur a reellement accepte le message |
| `Echec de l'envoi : ...` | Le message n'est pas parti, avec la cause |
| `Ignore: ...` | Le message n'entrait pas dans les criteres de reroutage |

Une ligne `Remis pour envoi` sans `Envoi confirme` signale un message bloque
avant transmission. Tout echec declenche aussi une notification.

## Limites connues

- **RCS et Chat Samsung ne sont pas concernes.** Si un correspondant ecrit via
  RCS ou via une messagerie tierce, le message n'est pas un SMS et l'application
  ne le voit pas. Le reroutage ne fonctionne que pour les vrais SMS.
- **MMS non geres.** Seuls les SMS texte sont transferes, sans piece jointe.
- **Chaque reroutage envoie un vrai SMS**, facture selon le forfait. Un message
  long est decoupe en plusieurs SMS.
- **Double SIM** : l'envoi utilise la SIM definie par defaut pour les SMS dans
  les parametres du telephone. Aucune selection de SIM dans l'application.
- **Les expediteurs alphanumeriques** (noms de service du type `SWILE`) sont
  geres, mais la correspondance se fait sur le libelle exact, accents et casse
  mis a part. Le libelle enregistre dans le contact doit donc etre identique a
  celui qui s'affiche comme expediteur du SMS, sans quoi le message ne sera pas
  reroute. Deux services portant le meme libelle seraient indistinguables.
- **Couverture de test partielle** : seule la comparaison des expediteurs est
  couverte par des tests unitaires (`./gradlew testDebugUnitTest`). La lecture
  du groupe de contacts, la reception et l'envoi se verifient manuellement
  selon la procedure ci-dessus.

## Protection contre les boucles

Si le numero de destination figure lui-meme dans le groupe surveille, ses
messages ne sont pas reroutes, ce qui evite un cycle d'envois infini.

## Recompiler

Prerequis : Android Studio, ou un JDK 17 et le SDK Android.

```bash
./gradlew assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/`.

Le chemin du SDK est lu depuis `local.properties`, non versionne.

## Structure

| Fichier | Role |
| --- | --- |
| `SmsReceiver.kt` | Reception du SMS, passage en tache de fond |
| `SmsSentReceiver.kt` | Resultat reel de l'envoi remonte par la telephonie |
| `Forwarder.kt` | Regles de reroutage et envoi |
| `ContactGroups.kt` | Numeros appartenant au groupe surveille |
| `Contacts.kt` | Nom affiche d'un correspondant |
| `PhoneNumbers.kt` | Comparaison d'expediteurs independante du format |
| `Prefs.kt` | Configuration locale et journal |
| `Notifier.kt` | Notification en cas d'echec d'envoi |
| `MainActivity.kt` | Ecran de configuration |
| `PhoneNumbersTest.kt` | Tests unitaires de la comparaison d'expediteurs |

## Confidentialite

Aucune donnee ne quitte le telephone en dehors du SMS envoye au numero
configure. L'application n'ouvre aucune connexion reseau.

Le journal ne conserve jamais le contenu des messages, mais il retient
l'expediteur, necessaire au diagnostic : il est donc a traiter comme une donnee
personnelle. Il se limite aux trente dernieres lignes et le bouton
`Effacer le journal` le vide immediatement.

La configuration et le journal sont stockes dans un fichier prive a
l'application, exclu des sauvegardes cloud comme des transferts vers un nouvel
appareil (`data_extraction_rules.xml`).

Le recepteur de SMS est protege par la permission systeme `BROADCAST_SMS` :
seule la couche telephonie peut le declencher. Le recepteur de resultat d'envoi
n'est pas exporte.
