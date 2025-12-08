# APP Replication - Microservices Application

A production-ready, full-stack microservices application demonstrating modern development practices from local development through containerization to Kubernetes deployment.

---

## 📋 Table of Contents

1. [Overview](#-overview)
2. [Running Locally](#-running-locally)
3. [Dockerization](#-dockerization)
4. [Docker Compose](#-docker-compose)
5. [Kubernetes Deployment](#-kubernetes-deployment)
6. [Production Deployment (AWS EKS)](#-production-deployment-aws-eks)
7. [Troubleshooting](#-troubleshooting)
8. [Developer FAQ](#-developer-faq)

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
