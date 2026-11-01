# ==============================================================================
# FlowDesk Frontend - Multi-Stage Containerization
# Build stage: Node 22 (Alpine)
# Runtime stage: Nginx (Alpine)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build production React / Vite static bundle
# ------------------------------------------------------------------------------
FROM node:22-alpine AS builder

WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm ci

# Build argument for Vite API base URL
# In containerized reverse-proxy deployment, frontend requests route to host port 3000
ARG VITE_API_BASE_URL=http://localhost:3000
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Copy frontend source files
COPY . .

# Compile TypeScript and build production bundle
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Serve static bundle via lightweight Nginx reverse proxy
# ------------------------------------------------------------------------------
FROM nginx:alpine

# Remove default Nginx website configuration
RUN rm -rf /etc/nginx/conf.d/*

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled assets from builder
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

# Health check using Nginx healthz endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:80/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
