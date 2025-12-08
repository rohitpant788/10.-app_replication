# NGINX Ingress Controller - Access Guide

## Quick Start

### Step 1: Run Port-Forward (as Administrator)

Open PowerShell **as Administrator** and run:

```powershell
cd F:\10. app_replication
.\infra\k8s\ingress\port-forward.ps1
```

**OR** manually:
```powershell
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
```

⚠️ **Keep the PowerShell window open!** Closing it will stop the port-forward.

---

### Step 2: Access Your Application

Once port-forward is running, access via:

| Service | URL | Description |
|---------|-----|-------------|
| **UI** | http://app.local | Main application interface |
| **Data API** | http://app.local/data/cases/next-id | Get next case ID |
| **RefData API** | http://app.local/refdata/countries | List countries |
| **Search API** | http://app.local/search/cases | Search cases |
| **File API** | http://app.local/file/* | File operations |

---

## How It Works

### Routing Flow

```
Browser (app.local)
    ↓
Hosts file (127.0.0.1)
    ↓
Port-forward (localhost:80 → ingress-nginx:80)
    ↓
NGINX Ingress Controller
    ↓
Ingress Rules (path-based routing)
    ↓
Backend Services (data, refdata, search, file, ui)
```

### Ingress Resources

**UI Ingress** (`ui-ingress.yaml`):
- Routes `app.local/` → `ui:80`
- Serves the React application

**Backend Ingress** (`backend-ingress.yaml`):
- Routes `app.local/data/*` → `data-service:9090`
- Routes `app.local/refdata/*` → `refdata-service:9092`
- Routes `app.local/search/*` → `search-service:9093`
- Routes `app.local/file/*` → `file-service:9091`

---

## Troubleshooting

### "Access Denied" when running port-forward
**Solution:** Run PowerShell as Administrator

### "Connection Refused" at app.local
**Check:**
1. Is port-forward running?
2. Is `app.local` in hosts file? (`C:\Windows\System32\drivers\etc\hosts`)
3. Is ingress controller pod running? `kubectl get pods -n ingress-nginx`

### Port 80 Already in Use
**Options:**
1. Stop the application using port 80 (check with `netstat -ano | findstr :80`)
2. Use NodePort instead: `http://localhost:30806`

---

## Alternative: Use NodePort (No Admin Required)

If you don't want to run port-forward, you can access via NodePort:

**NodePort URLs:**
- UI: http://localhost:30806
- Data: http://localhost:30806/data/cases/next-id
- RefData: http://localhost:30806/refdata/countries
- Search: http://localhost:30806/search/cases

No administrator privileges needed!

---

## Permanent Solution

For a permanent setup without port-forward, recreate the Kind cluster with port mappings:

```yaml
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
```

Then create cluster:
```powershell
kind create cluster --name app-cluster --config kind-config.yaml
```

This maps port 80/443 directly, eliminating the need for port-forward.
