# Enabling HTTPS for app.local - Complete Guide

## Overview

This guide shows how to enable HTTPS (`https://app.local`) for your local Kind cluster deployment using self-signed SSL certificates.

## Prerequisites

- OpenSSL installed (comes with Git Bash on Windows)
- Helm chart deployed
- NGINX Ingress Controller running

---

## Step 1: Generate Self-Signed SSL Certificate

### Using OpenSSL (Git Bash or PowerShell)

**Navigate to a suitable directory:**
```powershell
cd f:\10. app_replication\infra\k8s\ingress
```

**Generate private key and certificate:**
```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key \
  -out tls.crt \
  -subj "/CN=app.local/O=app-local" \
  -addext "subjectAltName=DNS:app.local,DNS:*.app.local"
```

**Explanation:**
- `-x509`: Generate self-signed certificate
- `-nodes`: No password protection
- `-days 365`: Valid for 1 year
- `-newkey rsa:2048`: 2048-bit RSA key
- `-keyout tls.key`: Private key output file
- `-out tls.crt`: Certificate output file
- `-subj`: Certificate subject (Common Name)
- `-addext`: Subject Alternative Name (required for modern browsers)

**Files created:**
- `tls.key` - Private key (keep this secure!)
- `tls.crt` - SSL certificate

---

## Step 2: Create Kubernetes TLS Secret

### Method 1: Using kubectl (Quick Test)

```powershell
kubectl create secret tls app-tls-secret \
  --cert=tls.crt \
  --key=tls.key
```

**Verify secret created:**
```powershell
kubectl get secret app-tls-secret
```

### Method 2: Using Helm (Recommended)

This integrates TLS into your Helm deployment.

---

## Step 3: Update Helm Chart for TLS

### Option A: Update Existing Helm Chart Templates

#### 3.1: Update `values.yaml`

Add TLS configuration to `infra/helm/app-chart/values.yaml`:

```yaml
# Ingress configuration
ingress:
  enabled: true
  className: nginx
  host: app.local
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
  tls:
    enabled: false  # Set to true to enable TLS
    secretName: app-tls-secret
  ui:
    enabled: true
  backend:
    enabled: true
    useRegex: true
```

#### 3.2: Update `values-local.yaml`

Override to enable TLS for local development:

```yaml
# Local Kind cluster specific overrides

# Use local images (don't pull from registry)
imagePullPolicy: Never

# Override fake-smtp to also use Never for local
fakeSmtp:
  image:
    pullPolicy: Never

# Database credentials for local Neon DB
database:
  url: jdbc:postgresql://ep-steep-paper-a4w8wloy-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
  username: neondb_owner
  password: npg_qnJyzb4A8Grk

# Enable HTTPS/TLS
ingress:
  tls:
    enabled: true
    secretName: app-tls-secret

# Enable fake SMTP for local development
fakeSmtp:
  enabled: true
```

#### 3.3: Update UI Ingress Template

Modify `infra/helm/app-chart/templates/ingress/ui-ingress.yaml`:

```yaml
{{- if and .Values.ingress.enabled .Values.ingress.ui.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ .Values.global.appName }}-ui-ingress
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  {{- if .Values.ingress.tls.enabled }}
  tls:
  - hosts:
    - {{ .Values.ingress.host }}
    secretName: {{ .Values.ingress.tls.secretName }}
  {{- end }}
  rules:
  - host: {{ .Values.ingress.host }}
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: {{ .Values.ui.name }}
            port:
              number: {{ .Values.ui.port }}
{{- end }}
```

#### 3.4: Update Backend Ingress Template

Modify `infra/helm/app-chart/templates/ingress/backend-ingress.yaml`:

```yaml
{{- if and .Values.ingress.enabled .Values.ingress.backend.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ .Values.global.appName }}-backend-ingress
  annotations:
    {{- if .Values.ingress.backend.useRegex }}
    nginx.ingress.kubernetes.io/use-regex: "true"
    {{- end }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  {{- if .Values.ingress.tls.enabled }}
  tls:
  - hosts:
    - {{ .Values.ingress.host }}
    secretName: {{ .Values.ingress.tls.secretName }}
  {{- end }}
  rules:
  - host: {{ .Values.ingress.host }}
    http:
      paths:
      {{- if .Values.dataService.enabled }}
      # Data Service
      - path: /data
        pathType: Prefix
        backend:
          service:
            name: {{ .Values.dataService.name }}
            port:
              number: {{ .Values.dataService.port }}
      {{- end }}
      
      {{- if .Values.refdataService.enabled }}
      # RefData Service
      - path: /refdata
        pathType: Prefix
        backend:
          service:
            name: {{ .Values.refdataService.name }}
            port:
              number: {{ .Values.refdataService.port }}
      {{- end }}
      
      {{- if .Values.searchService.enabled }}
      # Search Service
      - path: /search
        pathType: Prefix
        backend:
          service:
            name: {{ .Values.searchService.name }}
            port:
              number: {{ .Values.searchService.port }}
      {{- end }}
      
      {{- if .Values.fileService.enabled }}
      # File Service
      - path: /file
        pathType: Prefix
        backend:
          service:
            name: {{ .Values.fileService.name }}
            port:
              number: {{ .Values.fileService.port }}
      {{- end }}
{{- end }}
```

---

## Step 4: Deploy with HTTPS Enabled

### If Already Deployed, Upgrade:

```powershell
# 1. Create TLS secret (if not already done via kubectl)
cd f:\10. app_replication\infra\k8s\ingress
kubectl create secret tls app-tls-secret --cert=tls.crt --key=tls.key

# 2. Upgrade Helm release
cd f:\10. app_replication\infra\helm
helm upgrade app ./app-chart -f ./app-chart/values-local.yaml

# 3. Verify ingress has TLS configured
kubectl describe ingress app-ui-ingress
kubectl describe ingress app-backend-ingress
```

**Expected Output in Ingress:**
```
TLS:
  app-tls-secret terminates app.local
```

### Fresh Installation:

```powershell
# 1. Create certificates
cd f:\10. app_replication\infra\k8s\ingress
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=app.local/O=app-local" \
  -addext "subjectAltName=DNS:app.local,DNS:*.app.local"

# 2. Create TLS secret
kubectl create secret tls app-tls-secret --cert=tls.crt --key=tls.key

# 3. Install Helm chart (with TLS enabled in values-local.yaml)
cd f:\10. app_replication\infra\helm
helm install app ./app-chart -f ./app-chart/values-local.yaml
```

---

## Step 5: Update Port-Forward for HTTPS

### Port-Forward Both HTTP and HTTPS:

```powershell
# Run as Administrator
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
```

---

## Step 6: Access Application via HTTPS

### URLs:

- **UI**: https://app.local
- **Data API**: https://app.local/data/cases/next-id
- **RefData API**: https://app.local/refdata/countries
- **Search API**: https://app.local/search/cases
- **File API**: https://app.local/file/*

### Browser Warning (Expected):

Since we're using a self-signed certificate, browsers will show a security warning:

**Chrome/Edge:**
```
Your connection is not private
NET::ERR_CERT_AUTHORITY_INVALID
```

**How to Proceed:**
1. Click "Advanced"
2. Click "Proceed to app.local (unsafe)"

This is normal for self-signed certificates in development.

---

## Step 7: Trust Certificate (Optional - Removes Warning)

### Windows:

**Import certificate to Trusted Root:**

1. Double-click `tls.crt`
2. Click "Install Certificate"
3. Select "Local Machine"
4. Choose "Place all certificates in the following store"
5. Browse → "Trusted Root Certification Authorities"
6. Click Next → Finish

**Or via PowerShell (as Administrator):**
```powershell
Import-Certificate -FilePath "f:\10. app_replication\infra\k8s\ingress\tls.crt" -CertStoreLocation Cert:\LocalMachine\Root
```

After importing, restart your browser. The warning should disappear.

---

## Verification

### 1. Check TLS Secret:

```powershell
kubectl get secret app-tls-secret
kubectl describe secret app-tls-secret
```

**Expected:**
```
Name:         app-tls-secret
Type:         kubernetes.io/tls
Data
====
tls.crt:  1281 bytes
tls.key:  1704 bytes
```

### 2. Check Ingress TLS Configuration:

```powershell
kubectl get ingress app-ui-ingress -o yaml
```

**Should contain:**
```yaml
spec:
  tls:
  - hosts:
    - app.local
    secretName: app-tls-secret
```

### 3. Test HTTPS Access:

```powershell
# Using curl (ignore cert validation for self-signed)
curl -k https://app.local

# Should return HTML from React app
```

### 4. Browser Test:

1. Open browser
2. Navigate to https://app.local
3. Accept security warning (or it should be trusted if you imported cert)
4. Application should load over HTTPS

---

## Troubleshooting

### Issue: "Connection Refused"

**Check port-forward includes 443:**
```powershell
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 80:80 443:443
```

### Issue: Certificate Error Persists After Import

1. Clear browser cache
2. Restart browser completely
3. Verify certificate in Windows Certificate Manager:
   - Run: `certmgr.msc`
   - Check "Trusted Root Certification Authorities" → "Certificates"
   - Look for "app.local"

### Issue: HTTP Still Works, HTTPS Doesn't

**Check ingress controller logs:**
```powershell
kubectl logs -n ingress-nginx deployment/ingress-nginx-controller
```

**Verify TLS secret is in correct namespace:**
```powershell
kubectl get secret app-tls-secret -n default
```

### Issue: "Default backend - 404"

**Check ingress rules:**
```powershell
kubectl describe ingress app-ui-ingress
kubectl describe ingress app-backend-ingress
```

---

## Production Considerations

### For AWS EKS / Production:

Instead of self-signed certificates, use:

1. **AWS Certificate Manager (ACM)**:
   - Request certificate for your domain
   - Use AWS ALB Ingress Controller
   - Automatic certificate management

2. **cert-manager + Let's Encrypt**:
   ```yaml
   # values-prod.yaml
   ingress:
     tls:
       enabled: true
       secretName: app-tls-letsencrypt
     annotations:
       cert-manager.io/cluster-issuer: letsencrypt-prod
   ```

3. **External certificate provider**:
   - Upload certificates to Kubernetes secret
   - Reference in ingress

---

## Summary

**Changes Required for HTTPS:**

1. ✅ Generate self-signed certificate (`openssl`)
2. ✅ Create Kubernetes TLS secret (`kubectl create secret tls`)
3. ✅ Add TLS configuration to `values.yaml`
4. ✅ Enable TLS in `values-local.yaml`
5. ✅ Update ingress templates with TLS section
6. ✅ Port-forward both 80 and 443
7. ✅ Access via `https://app.local`
8. ✅ (Optional) Import certificate to avoid browser warnings

**Files Modified:**
- `infra/helm/app-chart/values.yaml` - Add TLS config
- `infra/helm/app-chart/values-local.yaml` - Enable TLS
- `infra/helm/app-chart/templates/ingress/ui-ingress.yaml` - Add TLS block
- `infra/helm/app-chart/templates/ingress/backend-ingress.yaml` - Add TLS block

**New Files:**
- `infra/k8s/ingress/tls.key` - Private key (DON'T COMMIT!)
- `infra/k8s/ingress/tls.crt` - SSL certificate

The application will be accessible at `https://app.local` with SSL encryption! 🔒
