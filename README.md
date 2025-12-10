# APP Replication - Microservices Application

A production-ready, full-stack microservices application demonstrating modern development practices from local development through containerization to Kubernetes deployment with Helm and HTTPS/SSL support.

---

## 📋 Table of Contents

1. [Overview](#-overview)
2. [Running Locally](#-running-locally)
3. [Dockerization](#-dockerization)
4. [Docker Compose](#-docker-compose)
5. [Kubernetes Deployment](#-kubernetes-deployment)
6. [Helm Deployment (Recommended)](#-helm-deployment-recommended)
7. [GitLab CI/CD Setup (Automated Deployments)](#-gitlab-cicd-setup-automated-deployments)
8. [Production Deployment (AWS EKS)](#-production-deployment-aws-eks)
9. [Troubleshooting](#-troubleshooting)
10. [Developer FAQ](#-developer-faq)

---

## 🎯 Overview

### What This Project Does

This is a case management system built with microservices architecture. Users can:
- Create and manage cases
- Upload files attached to cases
- Search for cases across the system
- Receive email notifications (via fake SMTP for testing)

### Architecture Diagram

```
                                    ┌─────────────┐
                                    │   Browser   │
                                    └──────┬──────┘
                                           │
                     ┌─────────────────────┼─────────────────────┐
                     │                     │                     │
              ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
              │  UI (React) │      │ Data Service│      │File Service │
              │  Port: 3000 │      │  Port: 9090 │      │ Port: 9091  │
              └─────────────┘      └──────┬──────┘      └──────┬──────┘
                                          │                     │
                                          │    Validates IDs    │
                                          ◄─────────────────────┘
                     ┌────────────────────┴─────────────────────┐
                     │                                           │
              ┌──────▼──────┐                            ┌──────▼──────┐
              │RefData Svc  │                            │Search Svc   │
              │ Port: 9092  │                            │ Port: 9093  │
              └─────────────┘                            └─────────────┘
                     │                                           │
                     └───────────────────┬───────────────────────┘
                                         │
                                  ┌──────▼──────┐
                                  │  PostgreSQL │
                                  │    (Neon)   │
                                  └─────────────┘
```

### Microservices

| Service | Port | Purpose |
|---------|------|---------|
| **data-service** | 9090 | Core CRUD operations for cases |
| **refdata-service** | 9092 | Reference data (countries, statuses) |
| **file-service** | 9091 | File upload/download management |
| **search-service** | 9093 | Search across all cases |
| **ui** | 3000 (dev) / 80 (prod) | React frontend |
| **fake-smtp** | 1025 (SMTP) / 1080 (Web UI) | Email testing |

### Tech Stack

**Backend:**
- Java 21
- Spring Boot 4.0.0
- Spring Data JPA
- PostgreSQL (Neon Cloud)
- Liquibase for DB migrations

**Frontend:**
- React 18
- Vite
- Nginx (for production serving)

**Infrastructure:**
- Docker & Docker Compose
- Kubernetes (Kind for local, ready for EKS)
- Helm (Package manager for Kubernetes)
- NGINX Ingress Controller

---

## 💻 Running Locally

### Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Node.js 18+
- npm 9+
- PostgreSQL access (or use Neon cloud)

### Step 1: Configure Database Credentials

Create `application-my.properties` in the **project root**:

```bash
# Copy the template
copy application-my.properties.example application-my.properties
```

Edit `application-my.properties`:
```properties
spring.datasource.url=jdbc:postgresql://YOUR_DB_HOST/YOUR_DB_NAME?sslmode=require&channel_binding=require
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

> ⚠️ **Security**: This file is in `.gitignore` - never commit it!

### Step 2: Run Each Microservice

Each Spring Boot service can run independently.

**Terminal 1 - Data Service:**
```bash
cd data
mvn spring-boot:run -Dspring.profiles.active=local
```
Access: http://localhost:9090

**Terminal 2 - RefData Service:**
```bash
cd refdata
mvn spring-boot:run -Dspring.profiles.active=local
```
Access: http://localhost:9092

**Terminal 3 - Search Service:**
```bash
cd search
mvn spring-boot:run -Dspring.profiles.active=local
```
Access: http://localhost:9093

**Terminal 4 - File Service:**
```bash
cd file
# Note: File service calls data-service at http://localhost:9090
mvn spring-boot:run -Dspring.profiles.active=local
```
Access: http://localhost:9091

**Terminal 5 - UI:**
```bash
cd ui
npm install
npm run dev
```
Access: http://localhost:3000

### How Services Communicate Locally

When running locally, services use `localhost` with actual ports:

```javascript
// In ui/src/config-local.json
{
  "dataServiceUrl": "http://localhost:9090",
  "fileServiceUrl": "http://localhost:9091",
  "refdataServiceUrl": "http://localhost:9092",
  "searchServiceUrl": "http://localhost:9093"
}
```

**Example: File Upload Flow**
1. User uploads file via UI (port 3000)
2. UI sends file to file-service (port 9091)
3. File-service validates case ID by calling data-service at `http://localhost:9090/data/cases/{id}`
4. File-service saves metadata to database
5. File-service returns success to UI

### Environment Variables

Services use Spring profiles to switch configurations:

- `local`: Uses `application-my.properties` (file-based credentials)
- `prod`: Uses environment variables (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)

---

## 🐳 Dockerization

### Why Dockerize?

Docker packages each service with its dependencies, ensuring:
- Consistency across environments
- No "works on my machine" issues
- Easy deployment and scaling

### Dockerfile Structure

Each microservice has a similar Dockerfile pattern.

#### Spring Boot Services Dockerfile

**Example: `data/Dockerfile`**

```dockerfile
# Line 1-2: Use official OpenJDK 21 base image
FROM openjdk:21-jdk-slim AS build

# Line 4-5: Set working directory
WORKDIR /app

# Line 7-8: Copy Maven wrapper and pom.xml first (for layer caching)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Line 10-11: Download dependencies (cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Line 13-14: Copy source code
COPY src ./src

# Line 16-17: Build the application
RUN ./mvnw clean package -DskipTests

# Line 19-23: Production stage - smaller final image
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Line 25-26: Expose port and run
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Key Concepts:**
- **Multi-stage build**: Build stage + Runtime stage = smaller image
- **Layer caching**: Dependencies download only when pom.xml changes
- **Port exposure**: `EXPOSE 9090` documents the port (doesn't publish it)

#### UI Dockerfile (React + Nginx)

**`ui/Dockerfile`**

```dockerfile
# Stage 1: Build React app
FROM  node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**UI Build Process:**
1. `npm run build` creates static files in `/dist`
2. Nginx serves these static files
3. Nginx also acts as reverse proxy to backend services

### Build Docker Images

```bash
# Build all services
docker build -t app-data-service:latest ./data
docker build -t app-refdata-service:latest ./refdata
docker build -t app-search-service:latest ./search
docker build -t app-file-service:latest ./file
docker build -t app-ui:latest ./ui
```

### Run Single Container (Example)

```bash
docker run -d \
  -p 9090:9090 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL='jdbc:postgresql://your-host/db' \
  -e DATABASE_USERNAME='user' \
  -e DATABASE_PASSWORD='pass' \
  app-data-service:latest
```

---

## 🚢 Docker Compose

### Why Docker Compose?

Instead of running 5+ `docker run` commands, Docker Compose orchestrates all services with one command.

### Folder Structure

```
infra/docker/
├── docker-compose.yml    # Service definitions
└── .env                  # Environment variables (gitignored)
```

### docker-compose.yml Structure

**Located at: `infra/docker/docker-compose.yml`**

```yaml
services:
  # Backend services
  data-service:
    build: ../../data
    ports:
      - "9090:9090"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - SPRING_MAIL_HOST=fake-smtp
      - SPRING_MAIL_PORT=1025
    depends_on:
      - fake-smtp

  refdata-service:
    build: ../../refdata
    ports:
      - "9092:9092"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}

  search-service:
    build: ../../search
    ports:
      - "9093:9093"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}

  file-service:
    build: ../../file
    ports:
      - "9091:9091"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - DATA_SERVICE_BASE-URL=http://data-service:9090

  # Frontend
  ui:
    build: ../../ui
    ports:
      - "3000:80"
    depends_on:
      - data-service
      - file-service
      - refdata-service
      - search-service

  # Email testing
  fake-smtp:
    image: reachfive/fake-smtp-server
    ports:
      - "1025:1025"  # SMTP port
      - "1080:1080"  # Web UI port
```

### Service-to-Service Communication

Inside Docker Compose, services use **service names** as hostnames:

```java
// In file-service application code
String url = "http://data-service:9090/data/cases/" + caseId;
```

Docker Compose creates a network where:
- `data-service` resolves to the data-service container's IP
- No need for `localhost` - containers see each other by service name

### Running with Docker Compose

**Start all services:**
```bash
cd infra/docker
docker-compose up --build
```

**Run in background:**
```bash
docker-compose up --build -d
```

**View logs:**
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f data-service
```

**Stop everything:**
```bash
docker-compose down
```

**Stop and remove volumes:**
```bash
docker-compose down -v
```

### Testing the Application

1. **Open UI**: http://localhost:3000
2. **Create a case** - fill in the form
3. **View cases** - list appears
4. **Check email**: http://localhost:1080 (Fake SMTP web UI)

### Debugging with Docker Compose

**Check running containers:**
```bash
docker-compose ps
```

**Execute commands inside container:**
```bash
docker-compose exec data-service sh
```

**View environment variables:**
```bash
docker-compose exec data-service env
```

**Test API from inside network:**
```bash
docker-compose exec data-service curl http://refdata-service:9092/refdata/countries
```

---

##  ☸️ Kubernetes Deployment

### Why Kubernetes?

Kubernetes (K8s) provides:
- **Auto-scaling**: Scale pods based on load
- **Self-healing**: Restart failed containers automatically
- **Load balancing**: Distribute traffic across pods
- **Rolling updates**: Zero-downtime deployments
- **Configuration management**: ConfigMaps & Secrets

### Kubernetes Concepts (Beginner-Friendly)

Think of Kubernetes like a smart container manager:

| Concept | Real-World Analogy |
|---------|-------------------|
| **Pod** | A box containing one or more containers |
| **Deployment** | Instructions for "I want 3 copies of this pod running" |
| **Service** | A phone number that routes calls to available pods |
| **ConfigMap** | A settings file that pods can read |
| **Secret** | A locked file with passwords |
| **Ingress** | A front door/receptionist routing visitors to the right room |

### Folder Structure

```
infra/k8s/
├── kind-config.yaml                 # Kind cluster configuration
├── db-secret.yaml                   # Database credentials (gitignored!)
│
├── config/                          # ConfigMaps (non-sensitive settings)
│   ├── data-service-configmap.yaml
│   ├── refdata-service-configmap.yaml
│   ├── search-service-configmap.yaml
│   └── file-service-configmap.yaml
│
├── data-service/
│   ├── deployment.yaml              # How to run data-service pods
│   └── service.yaml                 # How to access data-service
│
├── refdata-service/
│   ├── deployment.yaml
│   └── service.yaml
│
├── search-service/
│   ├── deployment.yaml
│   └── service.yaml
│
├── file-service/
│   ├── deployment.yaml
│   └── service.yaml
│
├── ui/
│   ├── deployment.yaml
│   └── service.yaml
│
├── fake-smtp/
│   ├── deployment.yaml
│   └── service.yaml
│
└── ingress/
    ├── backend-ingress.yaml         # Routes /data, /search, etc.
    ├── ui-ingress.yaml              # Routes / to UI
    ├── port-forward.ps1             # Helper script
    └── README.md                    # Ingress documentation
```

### 1. ConfigMaps - Non-Sensitive Configuration

**Purpose**: Store configuration that pods can read without hardcoding.

**Example: `infra/k8s/config/data-service-configmap.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: data-service-config
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  LOGGING_LEVEL_ROOT: "INFO"
  SERVER_PORT: "9090"
  SPRING_MAIL_HOST: "fake-smtp"
  SPRING_MAIL_PORT: "1025"
```

**What it stores:** Environment variables that aren't secret
**How pods use it:** Referenced in deployment's `env` section

### 2. Secrets - Sensitive Data

**Purpose**: Store passwords, API keys (base64 encoded).

> ⚠️ **Important**: Base64 is NOT encryption! It's just encoding. Never commit secrets to Git.

**Example: `infra/k8s/db-secret.yaml` (gitignored!)**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:  # Kubernetes auto-converts to base64
  DATABASE_URL: "jdbc:postgresql://your-host/db?sslmode=require"
  DATABASE_USERNAME: "your_username"
  DATABASE_PASSWORD: "your_password"
```

**How pods read it:**
```yaml
env:
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: DATABASE_PASSWORD
```

### 3. Deployments - Pod Management

**Purpose**: Define how many copies of a pod to run and how to run them.

**Example: `infra/k8s/data-service/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-service
spec:
  replicas: 1  # How many pods to run
  selector:
    matchLabels:
      app: data-service
  template:
    metadata:
      labels:
        app: data-service
    spec:
      containers:
      - name: data-service
        image: app-data-service:latest
        imagePullPolicy: Never  # Use local image (for Kind)
        ports:
        - containerPort: 9090
        env:
        # From ConfigMap
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: data-service-config
              key: SPRING_PROFILES_ACTIVE
        # From Secret
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: DATABASE_URL
        resources:
          requests:
            cpu: "250m"        # Min CPU guaranteed
            memory: "512Mi"    # Min RAM guaranteed
          limits:
            cpu: "1000m"       # Max CPU allowed
            memory: "1Gi"      # Max RAM allowed
```

**Key fields explained:**
- `replicas: 1` - Run 1 copy of this pod
- `imagePullPolicy: Never` - For Kind, use local Docker image
- `containerPort: 9090` - Pod listens on this port
- `resources` - Prevents pods from hogging all cluster resources

### 4. Services - Internal Load Balancers

**Purpose**: Provide a stable endpoint to access pods (even if pods restart/move).

**Example: `infra/k8s/data-service/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: data-service
spec:
  type: ClusterIP  # Only accessible within cluster
  selector:
    app: data-service  # Route traffic to pods with this label
  ports:
  - port: 9090           # Service listens on this port
    targetPort: 9090     # Forward to pod's containerPort
```

**Service Types:**
- **ClusterIP** (default): Only accessible inside cluster - `http://data-service:9090`
- **NodePort**: Exposes on each node's IP at a static port (30000-32767)
- **LoadBalancer**: Creates external load balancer (AWS ELB, etc.)

**How it works:**
```bash
# Other pods can call:
curl http://data-service:9090/data/cases/next-id
```

Service `data-service` routes to any pod labeled `app: data-service`.

### 5. Ingress Controller - External Routing

**Purpose**: Route external traffic (from browser) to internal services.

**Simple Explanation:**
- Without ingress: Need to port-forward every service individually
- With ingress: One entry point handles all routing

**Architecture:**

```
Browser (app.local) → Ingress Controller → Ingress Rules → Services → Pods
```

**Install NGINX Ingress Controller:**
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

**Backend Ingress: `infra/k8s/ingress/backend-ingress.yaml`**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-backend-ingress
spec:
  ingressClassName: nginx
  rules:
  - host: app.local
    http:
      paths:
      - path: /data
        pathType: Prefix
        backend:
          service:
            name: data-service
            port:
              number: 9090
      - path: /refdata
        pathType: Prefix
        backend:
          service:
            name: refdata-service
            port:
              number: 9092
      - path: /search
        pathType: Prefix
        backend:
          service:
            name: search-service
            port:
              number: 9093
      - path: /file
        pathType: Prefix
        backend:
          service:
            name: file-service
            port:
              number: 9091
```

**How routing works:**
- `http://app.local/data/cases` → data-service:9090
- `http://app.local/search/cases` → search-service:9093
- `http://app.local/refdata/countries` → refdata-service:9092

**UI Ingress: `infra/k8s/ingress/ui-ingress.yaml`**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ui-ingress
spec:
  ingressClassName: nginx
  rules:
  - host: app.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ui
            port:
              number: 80
```

### 6. Fake SMTP - Email Testing

**Purpose**: Test email functionality without sending real emails.

**Deployment: `infra/k8s/fake-smtp/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fake-smtp
spec:
  replicas: 1
  selector:
    matchLabels:
      app: fake-smtp
  template:
    metadata:
      labels:
        app: fake-smtp
    spec:
      containers:
      - name: fake-smtp
        image: reachfive/fake-smtp-server
        ports:
        - containerPort: 1025  # SMTP port
        - containerPort: 1080  # Web UI port
```

**Why port-forwarding is needed:**

Kind runs inside Docker, so services aren't directly accessible from your browser. Port-forwarding creates a tunnel:

```bash
# Forward fake-smtp web UI
kubectl port-forward svc/fake-smtp 30001:1080

# Access at: http://localhost:30001
```

---

## 🚀 End-to-End Kubernetes Setup

### Prerequisites

- Docker Desktop installed and running
- Kind installed (`choco install kind` on Windows)
- kubectl installed (`choco install kubernetes-cli`)

### Step 1: Create Kind Cluster

```bash
cd infra/k8s
kind create cluster --config kind-config.yaml --name app-cluster
```

**Verify cluster:**
```bash
kubectl get nodes
# Should show 3 nodes: 1 control-plane, 2 workers
```

### Step 2: Build and Load Docker Images

```bash
# Build all images
docker build -t app-data-service:latest ./data
docker build -t app-refdata-service:latest ./refdata
docker build -t app-search-service:latest ./search
docker build -t app-file-service:latest ./file
docker build -t app-ui:latest ./ui

# Load into Kind cluster
kind load docker-image app-data-service:latest --name app-cluster
kind load docker-image app-refdata-service:latest --name app-cluster
kind load docker-image app-search-service:latest --name app-cluster
kind load docker-image app-file-service:latest --name app-cluster
kind load docker-image app-ui:latest --name app-cluster

# Load fake-smtp
docker pull reachfive/fake-smtp-server
kind load docker-image reachfive/fake-smtp-server:latest --name app-cluster
```

### Step 3: Deploy Kubernetes Resources

```bash
# 1. Deploy FakeSMTP first (dependency)
kubectl apply -f infra/k8s/fake-smtp/

# 2. Create ConfigMaps
kubectl apply -f infra/k8s/config/

# 3. Create Secret (REQUIRED - contains DB credentials)
kubectl apply -f infra/k8s/db-secret.yaml

# 4. Deploy microservices
kubectl apply -f infra/k8s/data-service/
kubectl apply -f infra/k8s/refdata-service/
kubectl apply -f infra/k8s/search-service/
kubectl apply -f infra/k8s/file-service/

# 5. Deploy UI
kubectl apply -f infra/k8s/ui/

# 6. Install ingress controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# 7. Deploy ingress resources
kubectl apply -f infra/k8s/ingress/
```

### Step 4: Verify Deployment

```bash
# Check all pods are running
kubectl get pods

# Expected output:
# NAME                              READY   STATUS    RESTARTS   AGE
# data-service-xxx                  1/1     Running   0          2m
# refdata-service-xxx               1/1     Running   0          2m
# search-service-xxx                1/1     Running   0          2m
# file-service-xxx                  1/1     Running   0          2m
# ui-xxx                            1/1     Running   0          2m
# fake-smtp-xxx                     1/1     Running   0          2m

# Check services
kubectl get svc

# Check ingress
kubectl get ingress
```

### Step 5: Access via Ingress

**Add to hosts file** (`C:\Windows\System32\drivers\etc\hosts`):
```
127.0.0.1 app.local
```

**Start ingress port-forward** (Run as Administrator):
```powershell
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
```

**Access application:**
- **UI**: http://app.local
- **Data API**: http://app.local/data/cases/next-id
- **RefData API**: http://app.local/refdata/countries
- **Search API**: http://app.local/search/cases

### Step 6: Access Fake SMTP

```bash
kubectl port-forward svc/fake-smtp 30001:1080
```

**View emails**: http://localhost:30001

### Common Commands

**View pod logs:**
```bash
kubectl logs <pod-name>
kubectl logs -f <pod-name>  # Follow logs
```

**Describe pod (troubleshooting):**
```bash
kubectl describe pod <pod-name>
```

**Execute command in pod:**
```bash
kubectl exec -it <pod-name> -- sh
```

**Delete everything:**
```bash
kind delete cluster --name app-cluster
```

```

---

## 📦 Helm Deployment (Recommended)

### What is Helm?

Helm is the "package manager" for Kubernetes - think of it like `npm` for Node.js or `apt` for Ubuntu, but for K8s applications.

**Benefits over manual kubectl:**
- ✅ **One command deployment** instead of 7+ separate `kubectl apply` commands
- ✅ **Version control** - Track deployment versions and rollback easily
- ✅ **Environment management** - Switch between dev/staging/prod with different value files
- ✅ **Templating** - No more hardcoded values scattered across files
- ✅ **Upgrades & Rollbacks** - Built-in support for safe updates and rollbacks

### Helm Chart Structure

Located at `infra/helm/app-chart/`:

```
app-chart/
├── Chart.yaml              # Chart metadata (v1.0.0)
├── values.yaml            # Base configuration values
├── values-local.yaml      # Kind cluster overrides
├── README.md              # Comprehensive Helm documentation
├── .helmignore           # Files to exclude from packaging
└── templates/
    ├── _helpers.tpl      # Reusable template functions
    ├── NOTES.txt         # Post-installation instructions
    ├── deployments/      # 6 deployment templates
    ├── services/         # 6 service templates
    ├── configmaps/       # ConfigMap templates
    ├── secrets/          # Secret templates
    └── ingress/          # Ingress templates
```

### Prerequisites

```bash
# Install Helm (if not already installed)
choco install kubernetes-helm

# Verify installation
helm version
```

### Helm Deployment Steps

#### Step 1: Create Kind Cluster & Install Ingress

```bash
cd infra/k8s
kind create cluster --config kind-config.yaml --name app-cluster

# Install NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for ingress controller to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

#### Step 2: Build and Load Docker Images

```bash
# Build all images
docker build -t app-data-service:latest ./data
docker build -t app-refdata-service:latest ./refdata
docker build -t app-search-service:latest ./search
docker build -t app-file-service:latest ./file
docker build -t app-ui:latest ./ui

# Load into Kind cluster
kind load docker-image app-data-service:latest --name app-cluster
kind load docker-image app-refdata-service:latest --name app-cluster
kind load docker-image app-search-service:latest --name app-cluster
kind load docker-image app-file-service:latest --name app-cluster
kind load docker-image app-ui:latest --name app-cluster

# Load fake-smtp
docker pull reachfive/fake-smtp-server
kind load docker-image reachfive/fake-smtp-server:latest --name app-cluster
```

#### Step 3: Configure Database Credentials

Edit `infra/helm/app-chart/values-local.yaml`:

```yaml
database:
  url: "jdbc:postgresql://your-neon-db-host/your-database-name"
  username: "your-username"
  password: "your-password"
```

> ⚠️ **Security**: Never commit actual credentials! Consider using `--set` flags for sensitive data.

#### Step 4: Install the Helm Chart

```bash
# Navigate to helm directory
cd infra/helm

# Install with local values
helm install app ./app-chart -f ./app-chart/values-local.yaml

# Watch deployment
kubectl get pods -w
```

**Expected output:**
```
NAME                               READY   STATUS    RESTARTS   AGE
data-service-xxx                   1/1     Running   0          30s
refdata-service-xxx                1/1     Running   0          30s
search-service-xxx                 1/1     Running   0          30s
file-service-xxx                   1/1     Running   0          30s
ui-xxx                             1/1     Running   0          30s
fake-smtp-xxx                      1/1     Running   0          30s

Release "app" has been installed. Happy Helming!
```

#### Step 5: Access the Application

**Add to hosts file** (`C:\Windows\System32\drivers\etc\hosts`):
```
127.0.0.1 app.local
```

**Start ingress port-forward** (Run as Administrator):
```powershell
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
```

**Access application:**
- **UI**: http://app.local
- **Data API**: http://app.local/data/cases/next-id
- **RefData API**: http://app.local/refdata/countries
- **Search API**: http://app.local/search/cases
- **Fake SMTP Web UI**: 
  ```bash
  kubectl port-forward svc/fake-smtp 30001:1080
  # Then visit: http://localhost:30001
  ```

### Enabling HTTPS (Optional)

To access your application via `https://app.local` instead of `http://app.local`, follow these steps:

#### Step 1: Generate Self-Signed SSL Certificate

```bash
# Navigate to ingress directory
cd f:\10. app_replication\infra\k8s\ingress

# Generate certificate (valid for 1 year)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=app.local/O=app-local" \
  -addext "subjectAltName=DNS:app.local,DNS:*.app.local"
```

> 💡 **Tip**: If you don't have OpenSSL, it comes with Git Bash on Windows.

#### Step 2: Create Kubernetes TLS Secret

```powershell
kubectl create secret tls app-tls-secret --cert=tls.crt --key=tls.key
```

#### Step 3: Enable TLS in Helm Values

Edit `infra/helm/app-chart/values-local.yaml` and uncomment these lines:

```yaml
# Enable HTTPS/TLS (uncomment to enable)
ingress:
  tls:
    enabled: true
    secretName: app-tls-secret
```

#### Step 4: Upgrade Helm Release

```powershell
cd f:\10. app_replication\infra\helm
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml
```

#### Step 5: Port-Forward with HTTPS Support

```powershell
# Include port 443 for HTTPS (run as Administrator)
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
```

#### Step 6: Access via HTTPS

- **HTTPS**: https://app.local
- **Data API**: https://app.local/data/cases/next-id
- **RefData API**: https://app.local/refdata/countries
- **Search API**: https://app.local/search/cases

**Browser Security Warning:**

Since we're using a self-signed certificate, your browser will show a security warning. This is normal for development.

Click **"Advanced" → "Proceed to app.local (unsafe)"**

**To Remove Warning (Optional):**

Import certificate as trusted (run PowerShell as Administrator):

```powershell
Import-Certificate -FilePath "f:\10. app_replication\infra\k8s\ingress\tls.crt" -CertStoreLocation Cert:\LocalMachine\Root
```

Restart your browser after importing.

> 📚 **For detailed HTTPS documentation**, see `infra/k8s/ingress/HTTPS-SETUP-GUIDE.md`

#### HTTPS Troubleshooting Guide

This section covers real issues encountered during HTTPS setup and how to resolve them.

##### Issue 1: Helm Template Parse Error

**Error Message:**
```
Error: INSTALLATION FAILED: parse error at (app/templates/NOTES.txt:53): expected end; found {{else}}
```

**What This Means:**
- Missing `{{- end }}` tag in a Helm template
- Template syntax error preventing installation

**How to Fix:**

**Step 1**: Validate your Helm chart
```powershell
cd infra/helm
helm lint .\app-chart
```

**Step 2**: Check NOTES.txt for matching tags
- Ensure every `{{- if }}` has a matching `{{- end }}`
- Count opening and closing tags - they must match

**Step 3**: Fix and retry
```powershell
# After fixing template
helm lint .\app-chart
helm install app .\app-chart -f .\app-chart\values-local.yaml
```

##### Issue 2: Port 80 Already in Use

**Error Message:**
```
Unable to listen on port 80: bind: Only one usage of each socket address is normally permitted
```

**What This Means:**
- Another process is using port 80
- Usually a previous kubectl port-forward session

**How to Fix:**

**Option 1: Use Alternate Ports**
```powershell
# Forward to different local ports
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80 8443:443
```

**Access via:**
- HTTPS: https://app.local:8443
- HTTP: http://app.local:8080

**Option 2: Kill Process Using Port 80**

**Step 1**: Find process using port 80
```powershell
netstat -ano | findstr :80
```

**Output example:**
```
TCP    127.0.0.1:80    0.0.0.0:0    LISTENING    27876
```

**Step 2**: Kill the process
```powershell
taskkill /PID 27876 /F
```

**Step 3**: Retry with standard ports
```powershell
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
```

##### Issue 3: All Pods Missing After Helm Upgrade

**Symptom:**
```powershell
kubectl get pods
# No resources found in default namespace
```

**What This Means:**
- Helm release was accidentally uninstalled
- All deployments, services deleted
- TLS secret might still exist

**How to Fix:**

**Step 1**: Check if Helm release exists
```powershell
helm list
```

**If empty**, release was deleted.

**Step 2**: Verify TLS secret still exists
```powershell
kubectl get secret app-tls-secret
```

**If TLS secret exists**, you can skip certificate generation.

**Step 3**: Reinstall Helm chart
```powershell
cd infra/helm
helm install app .\app-chart -f .\app-chart\values-local.yaml
```

**Step 4**: Wait for pods to start
```powershell
kubectl get pods -w
# Wait until all show 1/1 READY
```

##### Issue 4: 404 Not Found on HTTPS

**Symptom:**
- Browser connects to https://app.local
- Gets "404 Not Found" from nginx

**Possible Causes & Solutions:**

**Cause 1: Pods Not Running**
```powershell
# Check pod status
kubectl get pods

# If pods are not running, check logs
kubectl describe pod ui-<pod-id>
kubectl logs ui-<pod-id>
```

**Cause 2: Ingress Not Configured**
```powershell
# Verify ingress has TLS configured
kubectl describe ingress app-ui-ingress

# Should show:
# TLS:
#   app-tls-secret terminates app.local
```

**If TLS section is missing**, check `values-local.yaml`:
```yaml
ingress:
  tls:
    enabled: true    # Must be true
    secretName: app-tls-secret
```

**Cause 3: Wrong Port-Forward**
```powershell
# Ensure port-forwarding includes port 443
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
# NOT just: kubectl port-forward ... 80:80
```

##### Issue 5: OpenSSL Not Found

**Error:**
```
openssl: The term 'openssl' is not recognized
```

**Solution: Use Git's OpenSSL**

Git for Windows includes OpenSSL:

```powershell
# Use full path to Git's OpenSSL
& "C:\Program Files\Git\usr\bin\openssl.exe" req -x509 -nodes -days 365 -newkey rsa:2048 `
  -keyout tls.key -out tls.crt `
  -subj "/CN=app.local/O=app-local" `
  -addext "subjectAltName=DNS:app.local,DNS:*.app.local"
```

**Or use PowerShell's built-in certificate**:
```powershell
$cert = New-SelfSignedCertificate -DnsName "app.local", "*.app.local" `
  -CertStoreLocation "cert:\CurrentUser\My" `
  -NotAfter (Get-Date).AddYears(1)

# Export for Kubernetes
$pwd = ConvertTo-SecureString -String "temp" -Force -AsPlainText
Export-PfxCertificate -Cert $cert -FilePath "app.local.pfx" -Password $pwd
```

Then convert PFX to PEM using Git's OpenSSL.

##### Issue 6: Browser Shows ERR_SSL_PROTOCOL_ERROR

**Symptom:**
- Browser can't establish secure connection
- ERR_SSL_PROTOCOL_ERROR instead of certificate warning

**Possible Causes:**

**Cause 1: Port 443 Not Forwarded**
```powershell
# Check your port-forward command includes 443
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
#                                                                          ^^^^^^ Important!
```

**Cause 2: TLS Secret Not Created**
```powershell
# Verify secret exists
kubectl get secret app-tls-secret

# If missing, recreate it
cd f:\10. app_replication\infra\k8s\ingress
kubectl create secret tls app-tls-secret --cert=tls.crt --key=tls.key
```

**Cause 3: Ingress Controller Not Running**
```powershell
# Check ingress controller status
kubectl get pods -n ingress-nginx

# Should show:
# ingress-nginx-controller-xxx   1/1   Running
```

##### Complete Verification Checklist

Use this checklist to verify HTTPS is working:

```powershell
# 1. Check TLS secret exists
kubectl get secret app-tls-secret
# Expected: TYPE = kubernetes.io/tls

# 2. Check all pods running
kubectl get pods
# Expected: All 6 pods showing 1/1 READY

# 3. Check ingress has TLS configured
kubectl describe ingress app-ui-ingress | Select-String "TLS"
# Expected: app-tls-secret terminates app.local

# 4. Check ingress ports
kubectl get ingress
# Expected: PORTS = 80, 443

# 5. Verify port-forward is active
netstat -ano | findstr "443"
# Expected: Shows listening on port 443 (or 8443 if using alternate)

# 6. Test HTTPS connection
curl -k https://app.local
# Expected: HTML response from UI (not 404)
```

##### Getting More Help

If you're still stuck after trying the above:

1. **Check Helm chart logs**:
   ```powershell
   helm status app
   helm get values app
   ```

2. **Check pod logs**:
   ```powershell
   kubectl logs deployment/ui
   kubectl logs deployment/data-service
   ```

3. **Check ingress controller logs**:
   ```powershell
   kubectl logs -n ingress-nginx deployment/ingress-nginx-controller
   ```

4. **Verify certificate validity**:
   ```powershell
   & "C:\Program Files\Git\usr\bin\openssl.exe" x509 -in tls.crt -text -noout
   # Check "Not After" date
   ```

5. **Review detailed guides**:
   - `infra/k8s/ingress/HTTPS-SETUP-GUIDE.md` - Complete setup guide
   - `infra/k8s/ingress/HTTPS-QUICKSTART.md` - Quick commands
   - Troubleshooting artifacts in conversation history

### Common Helm Operations

#### Upgrade After Changes

```bash
# Modify values in values-local.yaml, then upgrade
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml

# Preview changes before upgrading (dry run)
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml --dry-run --debug
```

#### Rollback to Previous Version

```bash
# View release history
helm history app

# Rollback to previous version
helm rollback app

# Rollback to specific revision
helm rollback app 2
```

#### Check Helm Release Status

```bash
# List all Helm releases
helm list

# Get release status
helm status app

# Get release values
helm get values app
```

#### Uninstall Release

```bash
# Remove all resources created by Helm
helm uninstall app

# Keep release history for future reference
helm uninstall app --keep-history
```

### Configuration via Values

#### Enabling/Disabling Services

```yaml
# In values-local.yaml
fakeSmtp:
  enabled: false  # Disable fake SMTP if using real SMTP

searchService:
  enabled: false  # Disable search service if not needed
```

#### Scaling Services

```yaml
# In values-local.yaml
dataService:
  replicas: 3  # Scale to 3 instances

resources:
  requests:
    cpu: 500m
    memory: 1Gi
  limits:
    cpu: 2000m
    memory: 2Gi
```

#### Using Custom Database Values

**Option 1: Edit values-local.yaml**
```yaml
database:
  url: "jdbc:postgresql://prod-db.aws/appdb"
  username: "prod-user"
  password: "secure-password"
```

**Option 2: Use --set flags** (more secure)
```bash
helm install app ./app-chart -f ./app-chart/values-local.yaml \
  --set database.url="jdbc:postgresql://prod-db/appdb" \
  --set database.username="user" \
  --set database.password="secure-pass"
```

### Comparison: Helm vs Manual kubectl

| Aspect | Manual kubectl | Helm Chart |
|--------|---------------|------------|
| **Deployment** | 7+ separate `kubectl apply` commands | Single `helm install` command |
| **Updates** | Manually edit and re-apply each file | `helm upgrade` with new values |
| **Rollback** | Manually revert each file | `helm rollback` (automatic) |
| **Version tracking** | Manual Git tags | Built-in revision history |
| **Environment switching** | Separate YAML files or manual edits | Values files (values-local.yaml, values-prod.yaml) |
| **Configuration** | Hardcoded in YAML | Templated with values |
| **Complexity** | Simple, but repetitive | More setup, but powerful |

### Troubleshooting

#### Template Rendering Errors

```bash
# Validate chart syntax
helm lint ./app-chart -f ./app-chart/values-local.yaml

# Preview rendered templates
helm template app ./app-chart -f ./app-chart/values-local.yaml
```

#### Deployment Failures

```bash
# Check pod status
kubectl get pods

# View pod logs
kubectl logs <pod-name>

# Describe pod for events
kubectl describe pod <pod-name>

# Check Helm release status
helm status app
```

#### Database Connection Issues

```bash
# Verify secret created correctly
kubectl get secret db-credentials -o yaml

# Check environment variables in pod
kubectl exec -it <pod-name> -- env | grep DATABASE
```

### Complete Troubleshooting Guide for Fresher Developers

This section covers real issues encountered during Helm deployment testing and how to resolve them step-by-step.

#### Issue 1: Helm Install Fails with "Secret exists and cannot be imported"

**Error Message:**
```
Error: INSTALLATION FAILED: unable to continue with install: Secret "db-credentials" in namespace "default" exists and cannot be imported into the current release: invalid ownership metadata
```

**What This Means:**
- You previously deployed using manual `kubectl apply` commands
- A secret called `db-credentials` already exists
- Helm requires clean ownership of all resources it manages

**How to Fix:**

**Step 1**: Check if the secret exists
```powershell
kubectl get secret db-credentials
```

**Step 2**: Delete the existing secret
```powershell
kubectl delete secret db-credentials
```

**Step 3**: Retry Helm installation
```powershell
cd infra/helm
helm install app ./app-chart -f ./app-chart/values-local.yaml
```

#### Issue 2: Helm Install Fails with "Resources already exist"

**What Happened:**
- You have existing deployments, services, or other resources from manual kubectl deployment
- Helm cannot take ownership of manually-created resources

**How to Fix - Complete Cleanup:**

**Step 1**: Check what's currently running
```powershell
kubectl get all
```

**Expected Output** (if you have old deployments):
```
NAME                                   READY   STATUS    RESTARTS   AGE
pod/data-service-xxx                   1/1     Running   0          5h
pod/ui-xxx                             1/1     Running   0          5h
...
deployment.apps/data-service           1/1     1         1          5h
...
service/data-service                   ClusterIP   10.96.x.x   9090/TCP   5h
```

**Step 2**: Delete ALL resources (deployments, services, pods)
```powershell
kubectl delete all --all
```

> ⚠️ **Warning**: This deletes EVERYTHING in the default namespace. Make sure you're in the correct cluster!

**Step 3**: Delete ConfigMaps
```powershell
kubectl delete configmap --all
```

**Step 4**: Delete Ingress resources
```powershell
kubectl delete ingress --all
```

**Step 5**: Delete Secrets
```powershell
kubectl delete secret db-credentials
```

**Step 6**: Verify namespace is clean
```powershell
kubectl get all
```

**Expected Output** (clean namespace):
```
NAME                 TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
service/kubernetes   ClusterIP   10.96.0.1    <none>        443/TCP   1d
```

Only the default `kubernetes` service should remain.

**Step 7**: Now install with Helm
```powershell
cd infra/helm
helm install app ./app-chart -f ./app-chart/values-local.yaml
```

#### Issue 3: No Kind Cluster Running

**Error:**
```
The connection to the server localhost:8080 was refused
```

**How to Fix:**

**Step 1**: Check if Kind cluster exists
```powershell
kind get clusters
```

**Expected Output** (if cluster exists):
```
app-cluster
```

**If no cluster exists**, create one:
```powershell
cd infra/k8s
kind create cluster --config kind-config.yaml --name app-cluster
```

**Step 2**: Verify kubectl is connected to the right cluster
```powershell
kubectl config current-context
```

**Expected Output**:
```
kind-app-cluster
```

**Step 3**: Check cluster nodes are ready
```powershell
kubectl get nodes
```

**Expected Output**:
```
NAME                        STATUS   ROLES           AGE   VERSION
app-cluster-control-plane   Ready    control-plane   1h    v1.33.1
app-cluster-worker          Ready    <none>          1h    v1.33.1
app-cluster-worker2         Ready    <none>          1h    v1.33.1
```

#### Issue 4: Docker Images Not Found in Kind

**Error in Pod:**
```
Failed to pull image "app-data-service:latest": image not found
```

**How to Fix:**

**Step 1**: Check if Docker images are built locally
```powershell
docker images --filter "reference=app-*"
```

**Expected Output**:
```
REPOSITORY            TAG       IMAGE ID       CREATED        SIZE
app-data-service      latest    b568ce5f06a8   2 hours ago    402MB
app-refdata-service   latest    11b8cf3aea31   2 hours ago    397MB
app-search-service    latest    3bd9f89b90e9   2 hours ago    385MB
app-file-service      latest    50e8b1aa5f36   2 hours ago    443MB
app-ui                latest    5f3998228023   2 hours ago    80.1MB
```

**If images are missing**, build them:
```powershell
docker build -t app-data-service:latest ./data
docker build -t app-refdata-service:latest ./refdata
docker build -t app-search-service:latest ./search
docker build -t app-file-service:latest ./file
docker build -t app-ui:latest ./ui
```

**Step 2**: Load images into Kind cluster
```powershell
kind load docker-image app-data-service:latest --name app-cluster
kind load docker-image app-refdata-service:latest --name app-cluster
kind load docker-image app-search-service:latest --name app-cluster
kind load docker-image app-file-service:latest --name app-cluster
kind load docker-image app-ui:latest --name app-cluster
```

**Step 3**: Verify images are loaded
```powershell
docker exec -it app-cluster-control-plane crictl images | grep app-
```

#### Issue 5: Ingress Controller Not Installed

**Symptom**: Ingress resources created but not working

**How to Fix:**

**Step 1**: Check if ingress controller is running
```powershell
kubectl get pods -n ingress-nginx
```

**Expected Output** (if installed):
```
NAME                                        READY   STATUS    RESTARTS   AGE
ingress-nginx-controller-86f7c48984-dmzzr   1/1     Running   0          1h
```

**If nothing shows**, install ingress controller:
```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

**Step 2**: Wait for ingress controller to be ready
```powershell
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

#### Issue 6: Pods Not Starting (Pending State)

**How to Debug:**

**Step 1**: Check pod status
```powershell
kubectl get pods
```

**If you see:**
```
NAME                     READY   STATUS    RESTARTS   AGE
data-service-xxx         0/1     Pending   0          2m
```

**Step 2**: Describe the pod to see why it's pending
```powershell
kubectl describe pod data-service-xxx
```

**Common Causes:**
1. **No nodes available**: Check `kubectl get nodes`
2. **Resource limits too high**: Pods can't fit on nodes
3. **Image pull errors**: Images not loaded into Kind

#### Issue 7: Pods Crashing or CrashLoopBackOff

**How to Debug:**

**Step 1**: Check pod logs
```powershell
kubectl logs data-service-74d84894f5-4nrcz
```

**Step 2**: If pod is restarting, see previous logs
```powershell
kubectl logs data-service-74d84894f5-4nrcz --previous
```

**Common Issues:**
1. **Database connection failure**: Check database credentials in values-local.yaml
2. **Missing environment variables**: Verify ConfigMap is loaded
3. **Application errors**: Check Spring Boot logs for Java exceptions

**Step 3**: Check events for the pod
```powershell
kubectl describe pod data-service-74d84894f5-4nrcz
```

Look at the "Events" section at the bottom.

#### Issue 8: Database Connection Errors

**Error in Logs:**
```
Cannot create PoolableConnectionFactory (The connection attempt failed.)
```

**How to Fix:**

**Step 1**: Verify database credentials in values-local.yaml
```yaml
database:
  url: "jdbc:postgresql://YOUR-CORRECT-HOST/neondb?sslmode=require"
  username: "YOUR-USERNAME"
  password: "YOUR-PASSWORD"
```

**Step 2**: Check if secret was created with correct values
```powershell
kubectl get secret db-credentials -o yaml
```

**Step 3**: Decode and verify secret contents
```powershell
# On Windows PowerShell
kubectl get secret db-credentials -o jsonpath='{.data.DATABASE_URL}' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }
```

**Step 4**: If credentials are wrong, update values-local.yaml and upgrade
```powershell
# Fix values-local.yaml, then:
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml
```

#### Useful Debugging Commands

**Check Helm Release Status:**
```powershell
# List all releases
helm list

# Get detailed status
helm status app

# See what values were used
helm get values app

# See all computed values
helm get values app --all

# View release history
helm history app
```

**Check All Resources Created by Helm:**
```powershell
kubectl get all -l app.kubernetes.io/instance=app
```

**Watch Pods Starting:**
```powershell
kubectl get pods -w
```
Press `Ctrl+C` to stop watching.

**Check ConfigMaps:**
```powershell
# List all ConfigMaps
kubectl get configmaps

# View ConfigMap contents
kubectl describe configmap data-service-config
```

**Check Services:**
```powershell
# List services
kubectl get svc

# Describe service to see endpoints
kubectl describe svc data-service
```

**Test Service Connectivity from Inside Cluster:**
```powershell
# Start a temporary pod
kubectl run test-pod --image=curlimages/curl:latest --rm -it -- sh

# Inside the pod, test service
curl http://data-service:9090/data/cases/next-id
```

**Check Ingress Resources:**
```powershell
# List ingress
kubectl get ingress

# Describe ingress
kubectl describe ingress app-backend-ingress
```

#### Complete Fresh Installation Checklist

Use this checklist for a clean Helm deployment:

**Prerequisites:**
- [ ] Docker Desktop running
- [ ] Kind cluster created and running
- [ ] NGINX Ingress Controller installed
- [ ] Docker images built locally
- [ ] Docker images loaded into Kind cluster
- [ ] Database credentials configured in values-local.yaml

**Installation Steps:**
```powershell
# 1. Verify cluster is ready
kubectl get nodes

# 2. Verify ingress controller is running
kubectl get pods -n ingress-nginx

# 3. Verify no conflicting resources exist
kubectl get all
kubectl get configmap
kubectl get secret
kubectl get ingress

# 4. If conflicts exist, clean them up:
kubectl delete all --all
kubectl delete configmap --all
kubectl delete ingress --all
kubectl delete secret db-credentials

# 5. Navigate to helm directory
cd f:\10. app_replication\infra\helm

# 6. Install Helm chart
helm install app ./app-chart -f ./app-chart/values-local.yaml

# 7. Watch pods start
kubectl get pods -w

# 8. Verify all pods are running (wait up to 2 minutes)
kubectl get pods

# 9. Check Helm release status
helm status app

# 10. Verify services created
kubectl get svc

# 11. Verify ingress created
kubectl get ingress
```

**Expected Timeline:**
- Helm install completes: ~10 seconds
- Pods start appearing: ~5 seconds
- All pods running (1/1): ~30-60 seconds

**Success Criteria:**
```powershell
kubectl get pods
```

**Should show:**
```
NAME                               READY   STATUS    RESTARTS   AGE
data-service-xxx                   1/1     Running   0          1m
refdata-service-xxx                1/1     Running   0          1m
search-service-xxx                 1/1     Running   0          1m
file-service-xxx                   1/1     Running   0          1m
ui-xxx                             1/1     Running   0          1m
fake-smtp-xxx                      1/1     Running   0          1m
```

All pods should show:
- **READY**: 1/1
- **STATUS**: Running
- **RESTARTS**: 0 (or low number)

#### What to Do If Things Go Wrong

**Nuclear Option - Complete Reset:**

If nothing works, start completely fresh:

```powershell
# 1. Delete Helm release
helm uninstall app

# 2. Delete Kind cluster
kind delete cluster --name app-cluster

# 3. Recreate cluster
cd f:\10. app_replication\infra\k8s
kind create cluster --config kind-config.yaml --name app-cluster

# 4. Install ingress controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# 5. Wait for ingress to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# 6. Rebuild and load images
docker build -t app-data-service:latest ./data
docker build -t app-refdata-service:latest ./refdata
docker build -t app-search-service:latest ./search
docker build -t app-file-service:latest ./file
docker build -t app-ui:latest ./ui

kind load docker-image app-data-service:latest --name app-cluster
kind load docker-image app-refdata-service:latest --name app-cluster
kind load docker-image app-search-service:latest --name app-cluster
kind load docker-image app-file-service:latest --name app-cluster
kind load docker-image app-ui:latest --name app-cluster

docker pull reachfive/fake-smtp-server
kind load docker-image reachfive/fake-smtp-server:latest --name app-cluster

# 7. Install Helm chart
cd f:\10. app_replication\infra\helm
helm install app ./app-chart -f ./app-chart/values-local.yaml

# 8. Watch deployment
kubectl get pods -w
```

#### Getting Help

If you're still stuck:

1. **Check pod logs** for specific error messages
2. **Describe the pod** to see Kubernetes events
3. **Verify prerequisites** checklist above
4. **Check Helm chart documentation**: `infra/helm/app-chart/README.md`
5. **Review test results**: See artifacts for successful deployment example

### For More Details

See the comprehensive Helm chart documentation:
- **Chart README**: `infra/helm/app-chart/README.md` - Detailed configuration guide
- **Implementation Plan**: Conversion details and design decisions
- **Walkthrough**: Complete validation and testing results

---

## ☁️ Production Deployment (AWS EKS)

### Migration from Kind to AWS EKS

| Component | Kind (Local) | AWS EKS (Production) |
|-----------|--------------|----------------------|
| **Cluster** | `kind create cluster` | `eksctl create cluster` |
| **Images** | Local Docker images | Amazon ECR (Elastic Container Registry) |
| **Ingress** | Port-forward | AWS Application Load Balancer (ALB) |
| **Secrets** | K8s Secrets | AWS Secrets Manager + External Secrets Operator |
| **Database** | Neon PostgreSQL | Amazon RDS PostgreSQL |
| **Storage** | emptyDir | Amazon EBS / EFS |
| **Monitoring** | kubectl logs | CloudWatch + Prometheus + Grafana |

### Step-by-Step EKS Migration

#### 1. Push Images to Amazon ECR

```bash
# Create ECR repository
aws ecr create-repository --repository-name app-data-service

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com

# Tag and push
docker tag app-data-service:latest YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/app-data-service:latest
docker push YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/app-data-service:latest
```

#### 2. Create EKS Cluster

```bash
eksctl create cluster \
  --name app-cluster \
  --region us-east-1 \
  --nodegroup-name app-nodes \
  --node-type t3.medium \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 5 \
  --managed
```

#### 3. Use AWS Secrets Manager

**Install External Secrets Operator:**
```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace
```

**Store secret in AWS:**
```bash
aws secretsmanager create-secret \
  --name app/database/credentials \
  --secret-string '{"username":"admin","password":"YOUR_PASSWORD","url":"jdbc:postgresql://rds-endpoint/db"}'
```

**Create ExternalSecret:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-credentials
spec:
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: db-credentials
  data:
  - secretKey: DATABASE_URL
    remoteRef:
      key: app/database/credentials
      property: url
```

#### 4. Setup AWS Load Balancer

**Install AWS Load Balancer Controller:**
```bash
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=app-cluster
```

**Update Ingress:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
  - host: app.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: ui
            port:
              number: 80
```

#### 5. Enable Auto-Scaling

**Horizontal Pod Autoscaler:**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: data-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: data-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

#### 6. Logging & Monitoring

**CloudWatch Container Insights:**
```bash
aws eks create-addon --cluster-name app-cluster \
  --addon-name amazon-cloudwatch-observability
```

**Prometheus + Grafana:**
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack
```

---

## 🔧 Troubleshooting

### Docker Issues

**Problem: Image build fails with "npm integrity checksum failed"**

Solution:
```bash
# Clear npm cache
npm cache clean --force

# Use npm install instead of npm ci in Dockerfile
RUN npm install
```

**Problem: Docker layer caching not working**

Solution: Order Dockerfile instructions correctly:
```dockerfile
# ✅ Good - dependencies cached
COPY pom.xml ./
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package

# ❌ Bad - always rebuilds everything
COPY . .
RUN mvn package
```

### Kubernetes Issues

**Problem: Pod stuck in "ImagePullBackOff"**

Check:
```bash
kubectl describe pod <pod-name>
# Look for: Failed to pull image

# For Kind, ensure image is loaded:
kind load docker-image app-data-service:latest --name app-cluster
```

**Problem: Pod in "CrashLoopBackOff"**

Debug:
```bash
# View logs
kubectl logs <pod-name>

# Check events
kubectl describe pod <pod-name>

# Common causes:
# - Missing environment variables
# - Database connection failure
# - Port already in use
```

**Problem: Can't access service from browser**

For Kind clusters:
```bash
# Services are NOT exposed automatically
# Need port-forward:
kubectl port-forward svc/ui 30000:80
```

**Problem: Ingress returns 503 Service Unavailable**

Check:
```bash
# 1. Is ingress controller running?
kubectl get pods -n ingress-nginx

# 2. Are backend pods ready?
kubectl get pods
# All should show 1/1 in READY column

# 3. Are services pointing to correct pods?
kubectl describe svc data-service
# Check Endpoints - should not be empty
```

### Environment Variable Issues

**View pod environment variables:**
```bash
kubectl exec <pod-name> -- env
```

**Check if ConfigMap is loaded:**
```bash
kubectl get configmap data-service-config -o yaml
```

**Check if Secret exists:**
```bash
kubectl get secret db-credentials
# DON'T use -o yaml in production (exposes secrets!)
```

### Network Issues

**Test service-to-service communication:**
```bash
# From inside a pod
kubectl exec -it <pod-name> -- sh
curl http://data-service:9090/data/cases/next-id
```

**DNS not resolving:**
```bash
# Check CoreDNS is running
kubectl get pods -n kube-system | grep coredns
```

---

## ❓ Developer FAQ

### Q: How do I change a port?

**For local development:**
1. Edit `application.properties`: `server.port=9090`
2. Update UI config: `ui/src/config-local.json`

**For Docker:**
1. Change `EXPOSE` in Dockerfile
2. Update port mapping in `docker-compose.yml`: `ports: - "9090:9090"`

**For Kubernetes:**
1. Update `deployment.yaml`: `containerPort: 9090`
2. Update `service.yaml`: `port: 9090` and `targetPort: 9090`
3. Update ingress if routing to this service

### Q: How do I add a new microservice?

1. **Create Spring Boot project** in new folder
2. **Add Dockerfile** (copy from existing service)
3. **Add to `docker-compose.yml`:**
   ```yaml
   my-new-service:
     build: ../../my-new-service
     ports:
       - "9094:9094"
   ```
4. **Create Kubernetes manifests:**
   - `infra/k8s/my-new-service/deployment.yaml`
   - `infra/k8s/my-new-service/service.yaml`
5. **Add to ingress** if needs external access

### Q: How do services talk to each other?

**Local:** Use `localhost:PORT`
```java
String url = "http://localhost:9090/data/cases/" + id;
```

**Docker Compose:** Use service name
```java
String url = "http://data-service:9090/data/cases/" + id;
```

**Kubernetes:** Use service name (same as Docker Compose)
```java
String url = "http://data-service:9090/data/cases/" + id;
```

### Q: Where do I add a new environment variable?

**Local:** Add to `application-my.properties`

**Docker Compose:**
```yaml
environment:
  - MY_NEW_VAR=value
```

**Kubernetes:**
1. Add to ConfigMap (non-sensitive):
   ```yaml
   # config/data-service-configmap.yaml
   data:
     MY_NEW_VAR: "value"
   ```
2. Reference in deployment:
   ```yaml
   env:
     - name: MY_NEW_VAR
       valueFrom:
         configMapKeyRef:
           name: data-service-config
           key: MY_NEW_VAR
   ```

### Q: How do I debug a failing container?

```bash
# View logs
docker logs <container-id>
kubectl logs <pod-name>

# Execute shell inside container
docker exec -it <container-id> sh
kubectl exec -it <pod-name> -- sh

# Check process
docker exec <container-id> ps aux
kubectl exec <pod-name> -- ps aux
```

### Q: Why is my database connection failing?

Check:
1. **Credentials correct?** View env vars: `kubectl exec <pod> -- env | grep DATABASE`
2. **Network access?** Test from pod: `kubectl exec <pod> -- curl <db-host>:5432`
3. **Secret applied?** `kubectl get secret db-credentials`
4. **SSL mode?** Neon requires `sslmode=require`

### Q: How do I update code and redeploy?

**Local:** Just restart the service

**Docker Compose:**
```bash
docker-compose up --build <service-name>
```

**Kubernetes:**
```bash
# 1. Rebuild image
docker build -t app-data-service:latest ./data

# 2. Load into Kind
kind load docker-image app-data-service:latest --name app-cluster

# 3. Restart deployment (force pod recreate)
kubectl rollout restart deployment data-service
```

### Q: What's the difference between ConfigMap and Secret?

| ConfigMap | Secret |
|-----------|--------|
| Non-sensitive data | Sensitive data (passwords, tokens) |
| Plain text | Base64 encoded |
| Can view with `-o yaml` | Don't view in prod |
| Example: port numbers, URLs | Example: DB password, API keys |

### Q: How do I scale a service?

**Docker Compose:** Edit `docker-compose.yml`:
```yaml
services:
  data-service:
    deploy:
      replicas: 3
```

**Kubernetes:**
```bash
kubectl scale deployment data-service --replicas=3
```

---

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Documentation](https://docs.docker.com/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Kind Documentation](https://kind.sigs.k8s.io/)
- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)

---

## 📄 License

This project is for educational purposes.

---

**Questions?** Check the [Developer FAQ](#-developer-faq) or contact the team.
