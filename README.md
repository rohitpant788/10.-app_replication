# APP Replication Application

A microservices-based application stack with data, refdata, file, and search services, along with a UI frontend.

---

## 🔒 Security Configuration (First-Time Setup)

> **IMPORTANT**: Before running the application, you must configure your database credentials externally to keep them secure and out of version control.

### Step 1: Create External Configuration File

The application uses an external configuration file to store sensitive database credentials. This file is **not tracked by git** to prevent accidental exposure.

1. **Locate the template file** in the project root:
   ```
   application-my.properties.example
   ```

2. **Copy it to create your local configuration**:
   ```powershell
   # From the project root directory (F:\10. app_replication)
   copy application-my.properties.example application-my.properties
   ```

3. **Edit `application-my.properties`** and replace the placeholders with your actual database credentials:
   ```properties
   spring.datasource.url=jdbc:postgresql://YOUR_ACTUAL_DB_HOST/YOUR_DB_NAME?sslmode=require&channel_binding=require
   spring.datasource.username=YOUR_ACTUAL_USERNAME
   spring.datasource.password=YOUR_ACTUAL_PASSWORD
   ```

### Step 2: Verify Security

✅ **Verify the following**:
- `application-my.properties` is listed in `.gitignore`
- `application-my.properties` is **NOT** showing up in `git status`
- Only `application-my.properties.example` (template) is tracked by git

### How It Works

#### Local Development (application-local.properties)

When running with `-Dspring.profiles.active=local`, microservices automatically import the external configuration:

```properties
# In application-local.properties
spring.config.import=optional:file:../application-my.properties
```

This file-based approach is perfect for local development where credentials are stored securely on your machine.

#### Production Deployment (application-prod.properties)

When running with `-Dspring.profiles.active=prod`, microservices use **environment variables**:

```properties
# In application-prod.properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
```

Set these environment variables in your production environment (Docker, Kubernetes, etc.). See [Production Deployment](#-additional-notes) section for examples.

### Benefits

This approach ensures:
- ✅ **No secrets in version control** - Credentials stay on your local machine
- ✅ **Easy updates** - Change credentials in one place for local dev
- ✅ **Profile-specific** - File-based for local, env vars for production
- ✅ **Team-friendly** - Each developer maintains their own credentials
- ✅ **Production-ready** - Uses industry-standard environment variables


---

## Quick Start with Docker Compose

**Docker Compose is the recommended way to run this application locally for development and testing.**

### Prerequisites

- **Docker Desktop**: Ensure Docker is installed and running.
- **Docker Compose**: Comes bundled with Docker Desktop.
- **Database Credentials**: Configure `application-my.properties` (see Security Configuration above)


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

# 2. Create Database Secret (REQUIRED!)
kubectl apply -f infra/k8s/db-secret.yaml

# 3. Deploy Microservices
kubectl apply -f infra/k8s/data-service/
kubectl apply -f infra/k8s/refdata-service/
kubectl apply -f  infra/k8s/file-service/
kubectl apply -f infra/k8s/search-service/

# 4. Deploy UI
kubectl apply -f infra/k8s/ui/
```

> **Important**: The `db-secret.yaml` contains database credentials and **must** be applied before deploying services.

Verify all pods are running:
```powershell
kubectl get pods
```

All pods should show `1/1` in the `READY` column and `Running` status.

---

### 5. Port Forwarding & Access

**⚠️ Important Note about Kind Clusters:**  
Kind (Kubernetes in Docker) runs inside Docker containers, so NodePort services **are NOT automatically exposed** to `localhost`. You **must** use `kubectl port-forward` to access services from your browser.

#### Access the UI

Open a **separate PowerShell/terminal window** and run:
```powershell
kubectl port-forward svc/ui 30000:80
```
> **Access URL**: [http://localhost:30000](http://localhost:30000)

> ⚠️ **Keep this terminal window open** - closing it will stop the port forward.

#### Access FakeSMTP Web Interface

Open **another PowerShell/terminal window**:
```powershell
kubectl port-forward svc/fake-smtp 30001:1080
```
> **Access URL**: [http://localhost:30001](http://localhost:30001)

> 💡 **Tip**: You can run these commands in the background or use separate terminal tabs to keep them all active simultaneously.

---

### 6. Testing the Application

1. **Open UI**: Go to [http://localhost:3000](http://localhost:3000)
2. **Create Case**:
kind delete cluster --name app-cluster
```

---

## 🔧 Troubleshooting

### Database Connection Issues

**Problem**: Service fails to start with "Could not open JDBC Connection" or similar database errors.

**Solution**:
1. Verify `application-my.properties` exists in the project root
2. Check that database credentials are correct
3. Ensure the PostgreSQL database is accessible from your network
4. Test database connectivity:
   ```powershell
   # From project root
   cd data
   ./mvnw spring-boot:run -Dspring.profiles.active=local
   ```

### Configuration File Not Found

**Problem**: Service logs show "application-my.properties not found" or "Could not import optional file".

**Solution**:
1. Ensure `application-my.properties` is in the **project root** directory (same level as `README.md`)
2. The path in `application.properties` uses `../application-my.properties` which goes up one directory from the service folder
3. Verify file location:
   ```
   F:\10. app_replication\application-my.properties  ✅ Correct
   F:\10. app_replication\data\application-my.properties  ❌ Wrong
   ```

### Secrets Accidentally Committed to Git

**Problem**: You accidentally committed database credentials to version control.

**Solution**:
1. **Immediately rotate your database credentials** (change password)
2. Remove the file from git history:
   ```powershell
   # Remove from current commit
   git rm --cached data/src/main/resources/application-local.properties
   git commit -m "Remove sensitive credentials"
   
   # For complete history cleanup (use with caution):
   git filter-branch --force --index-filter "git rm --cached --ignore-unmatch path/to/file.properties" --prune-empty --tag-name-filter cat -- --all
   ```
3. Verify `.gitignore` is properly configured
4. Force push (if working on a personal branch):
   ```powershell
   git push --force origin your-branch-name
   ```

### Verifying Security

Run these commands to ensure secrets are protected:

```powershell
# Check gitignore is working
git status

# Should NOT show:
# - application-my.properties
# - .vscode/

# Should show (as untracked or ignored):
# - application-my.properties.example (this is OK to commit)

# Verify no secrets in tracked files
git grep -i "npg_qnJyzb4A8Grk"  # Should return nothing
git grep -i "neondb_owner"      # Should return nothing
```

### Docker Build Issues

**Problem**: Docker build fails with "npm integrity checksum failed" or similar.

**Solution**: See the UI Dockerfile - it uses `npm install` instead of `npm ci` to handle missing package-lock.json files.

---

## 📝 Additional Notes

### For Team Members

When setting up this project:
1. Clone the repository
2. Copy `application-my.properties.example` to `application-my.properties`
3. Ask the team lead for database credentials
4. Never commit `application-my.properties` to version control

### For Production Deployment

The application is already configured to use **environment variables** in production profile. Here's how to set them up:

#### Option 1: Docker Compose

Update your `docker-compose.yml`:

```yaml
services:
  data-service:
    image: app-data-service:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=jdbc:postgresql://your-prod-host/neondb?sslmode=require
      - DATABASE_USERNAME=your_prod_user
      - DATABASE_PASSWORD=your_prod_password
    ports:
      - "9090:9090"
```

Or use `.env` file:

```bash
# .env file (add to .gitignore!)
DATABASE_URL=jdbc:postgresql://your-prod-host/neondb?sslmode=require
DATABASE_USERNAME=your_prod_user
DATABASE_PASSWORD=your_prod_password
```

```yaml
services:
  data-service:
    image: app-data-service:latest
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

#### Option 2: Kubernetes Secrets

Create a Kubernetes secret:

```bash
kubectl create secret generic db-credentials \
  --from-literal=DATABASE_URL='jdbc:postgresql://your-host/neondb?sslmode=require' \
  --from-literal=DATABASE_USERNAME='your_user' \
  --from-literal=DATABASE_PASSWORD='your_password'
```

Reference in deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-service
spec:
  template:
    spec:
      containers:
      - name: data-service
        image: app-data-service:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: DATABASE_URL
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: DATABASE_USERNAME
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: DATABASE_PASSWORD
```

#### Option 3: Docker Run

```bash
docker run -d \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL='jdbc:postgresql://your-host/neondb?sslmode=require' \
  -e DATABASE_USERNAME='your_user' \
  -e DATABASE_PASSWORD='your_password' \
  -p 9090:9090 \
  app-data-service:latest
```

#### Option 4: AWS Secrets Manager (Advanced)

1. Store credentials in AWS Secrets Manager
2. Add AWS SDK dependency to `pom.xml`
3. Update `application-prod.properties`:

```properties
spring.config.import=aws-secretsmanager:prod/database-credentials
```

#### Security Best Practices

- ✅ **Never commit** `.env` files containing real credentials
- ✅ **Use secret scanning** tools in your CI/CD pipeline (e.g., GitGuardian, TruffleHog)
- ✅ **Rotate credentials** regularly
- ✅ **Use least privilege** - production credentials should have minimal required permissions
- ✅ **Enable audit logging** for credential access
