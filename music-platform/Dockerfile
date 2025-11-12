# Étape 1 : Build de l'application Angular
FROM node:20-alpine AS build
WORKDIR /app

# Copier les fichiers de dépendances
COPY package*.json ./

# Installer les dépendances
RUN npm ci

# Copier le reste du code source
COPY . .

# Build Angular en mode production
RUN npm run build -- --configuration production

# Étape 2 : Servir le build avec Nginx
FROM nginx:alpine
# Copier les fichiers générés par Angular dans Nginx
COPY --from=build /app/dist/music-platform /usr/share/nginx/html

# Exposer le port HTTP
EXPOSE 80

# Lancer Nginx
CMD ["nginx", "-g", "daemon off;"]
