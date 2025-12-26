L’application permet de créer ou rejoindre une room, d’ajouter des sons dans une 
playlist partagée et de contrôler la lecture des musiques (play, pause, next).
Les utilisateurs peuvent également discuter en temps réel au sein d’une même room.

Prérequis pour lancer l’application
Docker et Docker Compose doivent être installés sur la machine.

Lancement de l’application
À la racine du projet, exécuter la commande suivante :
docker compose up -d --build
docker compose up -d


Une fois les conteneurs lancés, l’application est accessible à l’adresse suivante :

http://localhost:4200

Utilisation
À l’arrivée sur la page d’accueil, l’utilisateur peut créer une room.
Une fois la room créée, il est possible d’ajouter des musiques,
de discuter avec les autres participants et de contrôler la lecture.

Ajout de musiques
Un dossier nommé "song" est fourni avec une liste de fichiers audio.

Pour rendre ces musiques accessibles via une URL, se placer dans le dossier musique 
puis exécuter la commande suivante :
python -m http.server 9000


Les musiques sont alors accessibles à l’adresse suivante :

http://localhost:9000

Il suffit de sélectionner une musique,
copier son URL et la coller dans l’application afin de l’ajouter à la playlist.