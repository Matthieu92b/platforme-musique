# ===== DEV FRONTEND (no nginx) =====
FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

EXPOSE 4200

# Angular dev server accessible depuis l'extérieur du container
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--port", "4200", "--poll", "2000"]
