# Local Docker Registry Setup Guide

## Overview

This guide explains how to set up and use a local Docker Registry integrated with your Kind Kubernetes cluster. This allows GitLab CI/CD pipelines to build and push images locally without requiring external registries.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│               Kind Kubernetes Cluster                     │
│                                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Namespace: container-registry                      │  │
│  │                                                      │  │
│  │  ┌──────────────┐         ┌──────────────┐         │  │
│  │  │  Registry    │◄────────│  PVC (10GB)  │         │  │
│  │  │  Deployment  │         │  Storage     │         │  │
│  │  └──────────────┘         └──────────────┘         │  │
│  │         │                                           │  │
│  │         │ Service (NodePort: 30500)                 │  │
│  │         ▼                                           │  │
│  │  localhost:30500 ◄─── accessible from host         │  │
│  └────────────────────────────────────────────────────┘  │
│                                                            │
│  Containerd configured to use localhost:30500             │
└──────────────────────────────────────────────────────────┘
```

---

## Why Local Registry?

**Benefits**:
- ✅ Fast image pushes/pulls (no internet upload)
- ✅ Works offline
- ✅ No Docker Hub rate limits
- ✅ No external credentials needed
- ✅ Perfect for local CI/CD testing

**Comparison**:
| Aspect | Local Registry | Docker Hub | GitLab Registry |
|--------|---------------|------------|-----------------|
| Speed | ~2-5 sec | ~30-60 sec | ~20-40 sec |
| Cost | Free | Rate limited | Requires GitLab |
| Offline | Yes | No | No |

---

## Prerequisites

- Kind cluster not yet created (we'll create with registry support)
- OR existing cluster (will add registry to it)

---

## Setup Option 1: New Cluster with Registry

### Step 1: Delete Existing Cluster (if any)

```powershell
kind delete cluster --name app-cluster
```

### Step 2: Create Cluster with Registry Config

```powershell
cd f:\10. app_replication\infra\k8s

# Create cluster using registry-enabled config
kind create cluster --config kind-config-with-registry.yaml --name app-cluster
```

### Step 3: Deploy Registry to Cluster

```powershell
cd f:\10. app_replication\infra\registry

# Create namespace and deploy registry
kubectl apply -f registry-deployment.yaml
```

### Step 4: Wait for Registry to be Ready

```powershell
# Watch registry pod startup
kubectl get pods -n container-registry -w

# Wait for Running status (Ctrl+C to exit watch)
```

Expected output:
```
NAME                        READY   STATUS    RESTARTS   AGE
registry-5f7d8c9d4b-x7k2p   1/1     Running   0          30s
```

### Step 5: Verify Registry Access

```powershell
# Test registry is accessible
curl http://localhost:30500/v2/_catalog

# Expected: {"repositories":[]}
```

---

## Setup Option 2: Add Registry to Existing Cluster

If you already have a Kind cluster and don't want to recreate it:

### Step 1: Deploy Registry

```powershell
kubectl apply -f f:\10. app_replication\infra\registry\registry-deployment.yaml
```

### Step 2: Configure Containerd (Manual)

```powershell
# Get into each node
docker exec -it app-cluster-control-plane bash

# Edit containerd config
vi /etc/containerd/config.toml

# Add registry mirror configuration (as shown in kind-config-with-registry.yaml)

# Restart containerd
systemctl restart containerd

# Exit
exit

# Repeat for worker nodes:
# app-cluster-worker
# app-cluster-worker2
```

---

## Using the Registry

### Push Images to Local Registry

#### Method 1: Tag and Push

```powershell
# Build image
docker build -t app-data-service:latest ./data

# Tag for local registry
docker tag app-data-service:latest localhost:30500/app-data-service:latest

# Push to local registry
docker push localhost:30500/app-data-service:latest
```

#### Method 2: Build Directly for Registry

```powershell
# Build with registry tag directly
docker build -t localhost:30500/app-data-service:latest ./data

# Push
docker push localhost:30500/app-data-service:latest
```

### Pull Images from Registry

```powershell
# From host machine
docker pull localhost:30500/app-data-service:latest

# From within Kubernetes (in pod spec):
# image: localhost:30500/app-data-service:latest
# or
# image: registry.container-registry.svc.cluster.local:5000/app-data-service:latest
```

### List Images in Registry

```powershell
# List all repositories
curl http://localhost:30500/v2/_catalog

# List tags for specific image
curl http://localhost:30500/v2/app-data-service/tags/list
```

### Delete Images from Registry

```powershell
# Get image digest
curl -I -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
  http://localhost:30500/v2/app-data-service/manifests/latest

# Delete using digest
curl -X DELETE http://localhost:30500/v2/app-data-service/manifests/<digest>
```

---

## Integration with Helm

Update your Helm values to use local registry:

**File**: `infra/helm/app-chart/values-local.yaml`

```yaml
# Image registry prefix
imageRegistry: "localhost:30500"

# Update each service
dataService:
  image:
    repository: localhost:30500/app-data-service
    tag: latest

# Change pull policy
imagePullPolicy: Always  # or IfNotPresent
```

---

## Integration with GitLab CI

In `.gitlab-ci.yml`:

```yaml
variables:
  REGISTRY: "localhost:30500"

docker:data-service:
  script:
    - docker build -t $REGISTRY/app-data-service:$CI_COMMIT_SHORT_SHA ./data
    - docker push $REGISTRY/app-data-service:$CI_COMMIT_SHORT_SHA
```

---

## Persistence

Registry data is stored in a Persistent Volume:

### Check Storage Usage

```powershell
kubectl get pvc -n container-registry

# Expected:
# NAME           STATUS   VOLUME    CAPACITY   ACCESS MODES
# registry-pvc   Bound    pvc-xxx   10Gi       RWO
```

### Backup Registry Data

```powershell
# Create backup
kubectl exec -n container-registry deployment/registry -- \
  tar czf /tmp/registry-backup.tar.gz /var/lib/registry

# Copy backup to host
kubectl cp container-registry/registry-xxx:/tmp/registry-backup.tar.gz \
  ./registry- backup-$(Get-Date -Format 'yyyyMMdd').tar.gz
```

### Restore Registry Data

```powershell
# Copy backup to pod
kubectl cp ./registry-backup.tar.gz \
  container-registry/registry-xxx:/tmp/

# Extract
kubectl exec -n container-registry deployment/registry -- \
  tar xzf /tmp/registry-backup.tar.gz -C /
```

---

## Troubleshooting

### Issue 1: Cannot Push to Registry

**Error**: `connection refused` or `no route to host`

**Check**:
```powershell
# Verify registry pod is running
kubectl get pods -n container-registry

# Check service
kubectl get svc -n container-registry

# Test from host
curl http://localhost:30500/v2/
```

### Issue 2: Images Not Pulling in Pods

**Error**: `Failed to pull image "localhost:30500/app-data-service:latest"`

**Fix**:
1. Verify containerd configuration in Kind nodes
2. Use full service name: `registry.container-registry.svc.cluster.local:5000`
3. Check if image actually exists in registry

### Issue 3: Registry Full

**Error**: `no space left on device`

**Fix**:
```powershell
# Increase PVC size in registry-deployment.yaml
# Change: storage: 10Gi to storage: 20Gi

# Delete and recreate PVC (WARNING: loses data)
kubectl delete pvc registry-pvc -n container-registry
kubectl apply -f registry-deployment.yaml
```

### Issue 4: Can't Access from GitLab Runner

**Runner needs network access**:

If using GitLab Docker network:
```yaml
# In docker-compose-runner.yml
networks:
  - gitlab-network
  - kind  # Add Kind network
```

Or use host network:
```yaml
network_mode: "host"
```

---

## Registry Maintenance

### Clean Unused Images

```bash
# Enable garbage collection
kubectl set env deployment/registry -n container-registry \
  REGISTRY_STORAGE_DELETE_ENABLED=true

# Run garbage collection
kubectl exec -n container-registry deployment/registry -- \
  /bin/registry garbage-collect /etc/docker/registry/config.yml
```

### Monitor Registry

```powershell
# Check logs
kubectl logs -f -n container-registry deployment/registry

# Check disk usage
kubectl exec -n container-registry deployment/registry -- \
  du -sh /var/lib/registry
```

---

## Best Practices

### Development
- ✅ Use `imagePullPolicy: Always` to always get latest
- ✅ Tag images with commit SHA for traceability
- ✅ Keep a `latest` tag for quick testing

### CI/CD
- ✅ Tag with `$CI_COMMIT_SHORT_SHA`
- ✅ Also tag with branch name
- ✅ Keep production tags separate

### Cleanup
- ✅ Regularly remove old images
- ✅ Monitor disk usage
- ✅ Set up automated cleanup job

---

## Next Steps

1. ✅ Registry deployed and accessible
2. ➡️ Push your first image
3. ➡️ Update Helm values to use registry
4. ➡️ Configure GitLab CI to push images
5. ➡️ Deploy application using registry images

---

## References

- Docker Registry API: https://docs.docker.com/registry/spec/api/
- Kind Registry Guide: https://kind.sigs.k8s.io/docs/user/local-registry/
- Containerd Config: https://github.com/containerd/containerd/blob/main/docs/cri/registry.md
