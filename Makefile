# Démarre tous les services (build + up)
up:
	docker-compose up --build -d

# Stoppe et supprime tous les conteneurs
down:
	docker-compose down

# Affiche les logs de tous les conteneurs
log:
	docker-compose logs -f --tail=100

# Affiche uniquement les logs RabbitMQ
log-rabbit:
	docker-compose logs -f rabbitmq

# Redémarre tous les services sans rebuild
restart:
	docker-compose restart

# Supprime tout (conteneurs + images + volumes)
clean:
	docker-compose down -v --rmi all

# Liste les conteneurs actifs
ps:
	docker ps

# Reconstruit uniquement sans lancer
build:
	docker-compose build
