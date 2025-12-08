# APP Replication Application

A microservices-based application stack with data, refdata, file, and search services, along with a UI frontend.

---

## Quick Start with Docker Compose

**Docker Compose is the recommended way to run this application locally for development and testing.**

### Prerequisites

- **Docker Desktop**: Ensure Docker is installed and running.
- **Docker Compose**: Comes bundled with Docker Desktop.

### Building and Running the Application

1. **Navigate to the Docker directory**:
   ```powershell
   cd infra/docker
   ```

2. **Build and start all services**:
   ```powershell
   docker-compose up --build
   ```
   
   > **Note**: Use `--build` (two dashes) to rebuild images before starting containers.

3. **Run in detached mode** (background):
   ```powershell
   docker-compose up --build -d
   ```

4. **View logs**:
   ```powershell
   # All services
   docker-compose logs -f
   
   # Specific service
   docker-compose logs -f data-service
   ```

5. **Stop all services**:
   ```powershell
   docker-compose down
   ```

### Accessing Services

Once running, access the following endpoints:

| Service | URL |
|---------|-----|
| **UI** | [http://localhost:3000](http://localhost:3000) |
| **Data Service** | [http://localhost:9090](http://localhost:9090) |
| **RefData Service** | [http://localhost:9092](http://localhost:9092) |
| **Search Service** | [http://localhost:9093](http://localhost:9093) |
| **File Service** | [http://localhost:9091](http://localhost:9091) |
| **FakeSMTP Web UI** | [http://localhost:1080](http://localhost:1080) |

### Testing the Application

1. **Open the UI**: Go to [http://localhost:3000](http://localhost:3000)
2. **Create a Case**:
   - Fill in the required form fields
   - Attach a file (tests File Service → Data Service connectivity)
   - Submit the form
3. **Verify Email Notification**:
   - Open FakeSMTP UI at [http://localhost:1080](http://localhost:1080)
   - Check that the "Case Created" email was received

### Docker Compose Commands Reference

```powershell
# Build images only
docker-compose build

# Start services without rebuilding
docker-compose up

# Rebuild and start services
docker-compose up --build

# Stop services (keeps containers)
docker-compose stop

# Stop and remove containers
docker-compose down

# Remove containers, networks, and volumes
docker-compose down -v

# View running services
docker-compose ps

# Execute command in a running container
docker-compose exec data-service sh
```

---

## Kubernetes Deployment

**For production-like deployment or learning Kubernetes, follow these instructions.**

### Prerequisites

1. **Docker Desktop**: Ensure Docker is installed and running.
2. **Kind**: Kubernetes in Docker.
3. **Kubectl**: Kubernetes command-line tool.

#### Installing Kind & Kubectl (Windows)

Using **Chocolatey**:
```powershell
choco install kind
choco install kubernetes-cli
```

Or manually:
- [Kind Installation Guide](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [Kubectl Installation Guide](https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/)

---

### 1. Setup Multi-Node Cluster

Create a cluster with **1 Control Plane (Master)** and **2 Worker Nodes**.

1. Navigate to the `infra/k8s` directory:
   ```powershell
   cd infra/k8s
   ```

2. Create the cluster using the provided config file:
   ```powershell
   kind create cluster --config kind-config.yaml --name app-cluster
   ```

3. Verify the nodes are ready:
   ```powershell
   kubectl get nodes
   ```
   *Output should show 3 nodes: 1 control-plane, 2 workers.*

---

### 2. Build Docker Images

Build all service images locally:

```powershell
# Navigate to project root
cd f:\10. app_replication

# Build Data Service
docker build -t app-data-service:latest ./data

# Build RefData Service
docker build -t app-refdata-service:latest ./refdata

# Build File Service
docker build -t app-file-service:latest ./file

# Build Search Service
docker build -t app-search-service:latest ./search

# Build UI
docker build -t app-ui:latest ./ui
```

*Note: You can skip this if you already built them via `docker-compose build`.*

---

### 3. Load Images into Kind Cluster

Load images from your local Docker daemon into the Kind cluster:

```powershell
kind load docker-image app-data-service:latest --name app-cluster
kind load docker-image app-refdata-service:latest --name app-cluster
kind load docker-image app-file-service:latest --name app-cluster
kind load docker-image app-search-service:latest --name app-cluster
kind load docker-image app-ui:latest --name app-cluster
kind load docker-image reachfive/fake-smtp-server:latest --name app-cluster
```

> **Note**: Pull `reachfive/fake-smtp-server` first if not present locally:
> ```powershell
> docker pull reachfive/fake-smtp-server
> ```

---

### 4. Deploy Services

Apply the Kubernetes manifests:

```powershell
# 1. Deploy FakeSMTP (Dependency)
kubectl apply -f infra/k8s/fake-smtp/

# 2. Deploy Microservices
kubectl apply -f infra/k8s/data-service/
kubectl apply -f infra/k8s/refdata-service/
kubectl apply -f infra/k8s/file-service/
kubectl apply -f infra/k8s/search-service/

# 3. Deploy UI
kubectl apply -f infra/k8s/ui/
```

Verify everything is running:
```powershell
kubectl get pods
kubectl get svc
```

---

### 5. Port Forwarding & Access

Access services via **Port Forwarding**:

#### Access the UI
Open a terminal and run:
```powershell
kubectl port-forward svc/ui 3000:80
```
> **Access URL**: [http://localhost:3000](http://localhost:3000)

#### Access FakeSMTP Emails
Open another terminal:
```powershell
kubectl port-forward svc/fake-smtp 1080:1080
```
> **Access URL**: [http://localhost:1080](http://localhost:1080)

---

### 6. Testing the Application

1. **Open UI**: Go to [http://localhost:3000](http://localhost:3000)
2. **Create Case**:
   - Fill in the form
   - Attach a file (tests `File Service` → `Data Service` connectivity inside K8s)
   - Submit
3. **Verify Email**:
   - Go to [http://localhost:1080](http://localhost:1080)
   - Check if the "Case Created" email arrived

---

### Cleanup

To stop and remove the entire cluster:
```powershell
kind delete cluster --name app-cluster
```
