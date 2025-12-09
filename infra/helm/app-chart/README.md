# App Microservices Helm Chart

A comprehensive Helm chart for deploying the App microservices application to Kubernetes.

## Prerequisites

- Kubernetes cluster (Kind, minikube, or cloud provider)
- Helm 3.x installed
- kubectl configured to access your cluster
- NGINX Ingress Controller installed (for ingress support)

## Quick Start

### 1. Install NGINX Ingress Controller (if not already installed)

For Kind cluster:
```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

For other clusters, see: https://kubernetes.github.io/ingress-nginx/deploy/

### 2. Configure Database Credentials

Edit `values-local.yaml` and update the database configuration:

```yaml
database:
  url: "jdbc:postgresql://your-neon-db-host/your-database-name"
  username: "your-username"
  password: "your-password"
```

**Important**: Never commit actual credentials to version control!

### 3. Install the Chart

From the `infra/helm` directory:

```powershell
# Install with local values
helm install app ./app-chart -f ./app-chart/values-local.yaml

# Or install with custom values via --set
helm install app ./app-chart -f ./app-chart/values-local.yaml \
  --set database.url="jdbc:postgresql://your-db/dbname" \
  --set database.username="user" \
  --set database.password="pass"
```

### 4. Verify Deployment

```powershell
# Check all resources
kubectl get all

# Watch pod status
kubectl get pods -w

# Check ingress
kubectl get ingress
```

### 5. Access the Application

#### Option A: Using Ingress (Recommended)

1. Add to hosts file (`C:\Windows\System32\drivers\etc\hosts`):
   ```
   127.0.0.1 app.local
   ```

2. Port-forward ingress controller (run as Administrator):
   ```powershell
   kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
   ```

3. Access:
   - UI: http://app.local
   - Data API: http://app.local/data/cases/next-id
   - RefData API: http://app.local/refdata/countries

#### Option B: Using Port-Forward

```powershell
# UI
kubectl port-forward svc/ui 80:80

# Data Service
kubectl port-forward svc/data-service 9090:9090
```

## Chart Structure

```
app-chart/
├── Chart.yaml              # Chart metadata
├── values.yaml            # Default configuration values
├── values-local.yaml      # Local/Kind cluster overrides
├── .helmignore           # Files to exclude from packaging
├── README.md             # This file
└── templates/
    ├── _helpers.tpl      # Template helper functions
    ├── NOTES.txt        # Post-installation notes
    ├── deployments/     # Deployment resources
    ├── services/        # Service resources
    ├── configmaps/      # ConfigMap resources
    ├── secrets/         # Secret resources
    └── ingress/         # Ingress resources
```

## Configuration

### Key Values

| Parameter | Description | Default |
|-----------|-------------|---------|
| `imagePullPolicy` | Image pull policy | `IfNotPresent` |
| `database.url` | Database JDBC URL | `""` (required) |
| `database.username` | Database username | `""` (required) |
| `database.password` | Database password | `""` (required) |
| `ingress.enabled` | Enable ingress | `true` |
| `ingress.host` | Ingress host | `app.local` |
| `serviceType` | Kubernetes service type | `ClusterIP` |

### Service-Specific Configuration

Each service (dataService, refdataService, searchService, fileService, ui) has its own configuration section:

```yaml
dataService:
  enabled: true           # Enable/disable service
  replicas: 1            # Number of replicas
  port: 9090            # Service port
  image:
    repository: app-data-service
    tag: latest
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 1Gi
```

### Enabling/Disabling Services

To disable a service, set `enabled: false`:

```yaml
# In values-local.yaml
fakeSmtp:
  enabled: false  # Disable fake SMTP in production
```

## Common Operations

### Upgrade Release

After making changes to values or templates:

```powershell
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml
```

### Rollback to Previous Version

```powershell
# List release history
helm history app

# Rollback to previous version
helm rollback app

# Rollback to specific revision
helm rollback app 2
```

### Uninstall Release

```powershell
helm uninstall app
```

### Dry Run / Template Preview

Preview what will be deployed without installing:

```powershell
# Render templates
helm template app ./app-chart -f ./app-chart/values-local.yaml

# Dry run with validation
helm install app ./app-chart -f ./app-chart/values-local.yaml --dry-run --debug
```

### Validate Chart

```powershell
# Lint chart for best practices
helm lint ./app-chart -f ./app-chart/values-local.yaml
```

## Environment-Specific Deployments

### Local (Kind Cluster)

Use `values-local.yaml`:
- Image pull policy: `Never` (uses local images)
- Fake SMTP enabled
- Uses local database credentials

```powershell
helm install app ./app-chart -f ./app-chart/values-local.yaml
```

### Production (AWS EKS)

Create `values-prod.yaml`:

```yaml
imagePullPolicy: Always

database:
  url: "jdbc:postgresql://prod-db.aws/appdb"
  username: "prod-user"
  password: "secure-password"

fakeSmtp:
  enabled: false  # Use real SMTP in production

ingress:
  host: app.production.com
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod

dataService:
  replicas: 3  # Scale up for production
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 2Gi
```

Deploy:
```powershell
helm install app ./app-chart -f ./app-chart/values-prod.yaml
```

## Troubleshooting

### Pods Not Starting

```powershell
# Check pod status
kubectl get pods

# View pod logs
kubectl logs <pod-name>

# Describe pod for events
kubectl describe pod <pod-name>
```

### Database Connection Issues

Verify secret is created correctly:
```powershell
kubectl get secret db-credentials -o yaml
```

Check database credentials are accessible from pod:
```powershell
kubectl exec -it <pod-name> -- env | grep DATABASE
```

### Ingress Not Working

1. Verify ingress controller is running:
   ```powershell
   kubectl get pods -n ingress-nginx
   ```

2. Check ingress resource:
   ```powershell
   kubectl describe ingress app-backend-ingress
   ```

3. Ensure port-forward is running (local):
   ```powershell
   kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80
   ```

### Template Rendering Errors

```powershell
# Validate template syntax
helm template app ./app-chart -f ./app-chart/values-local.yaml

# Debug with verbose output
helm install app ./app-chart -f ./app-chart/values-local.yaml --dry-run --debug
```

## Security Best Practices

1. **Never commit credentials**: Keep `values-local.yaml` and `values-prod.yaml` out of version control if they contain sensitive data
2. **Use external secrets**: Consider using [External Secrets Operator](https://external-secrets.io/) for production
3. **Encrypt secrets**: Use tools like [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) or cloud provider secret managers
4. **RBAC**: Implement proper Kubernetes RBAC for production deployments

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review pod logs: `kubectl logs <pod-name>`
3. Validate chart: `helm lint ./app-chart`

## License

Internal use only - App Team
