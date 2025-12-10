# Quick HTTPS Setup for app.local

## Option 1: Quick Commands (Copy-Paste Ready)

```powershell
# 1. Generate SSL certificate
cd f:\10. app_replication\infra\k8s\ingress
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout tls.key -out tls.crt -subj "/CN=app.local/O=app-local" -addext "subjectAltName=DNS:app.local,DNS:*.app.local"

# 2. Create Kubernetes TLS secret
kubectl create secret tls app-tls-secret --cert=tls.crt --key=tls.key

# 3. Enable TLS in values-local.yaml (edit file and uncomment TLS section)
# Then upgrade Helm:
cd f:\10. app_replication\infra\helm
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml

# 4. Port-forward with HTTPS (443)
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443

# 5. Access via HTTPS
# https://app.local
```

## Option 2: Enable TLS in values-local.yaml

Add these lines to `infra/helm/app-chart/values-local.yaml`:

```yaml
# Enable HTTPS/TLS
ingress:
  tls:
    enabled: true
    secretName: app-tls-secret
```

## Accessing the App

- HTTP: http://app.local (still works)  
- HTTPS: https://app.local (new!)

## Browser Warning

You'll see a security warning because it's a self-signed certificate. Click "Advanced" → "Proceed to app.local".

## Remove Warning (Optional)

Import certificate as trusted:
```powershell
Import-Certificate -FilePath "f:\10. app_replication\infra\k8s\ingress\tls.crt" -CertStoreLocation Cert:\LocalMachine\Root
```

Then restart your browser.

---

**See `HTTPS-SETUP-GUIDE.md` for detailed documentation.**
