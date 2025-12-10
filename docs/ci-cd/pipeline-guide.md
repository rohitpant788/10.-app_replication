# GitLab CI/CD Pipeline Guide

## Overview

This guide explains how to use the GitLab CI/CD pipeline for automated builds, tests, and deployments of the microservices application.

---

## Pipeline Overview

```
Git Push → Build → Test → Docker → Deploy
            (5-10 min total)
```

**Stages**:
1. **build**: Compile Java (Maven) and JavaScript (npm) projects
2. **test**: Run unit tests, generate coverage reports
3. **docker**: Build and push Docker images to local registry
4. **deploy**: Deploy to Kind cluster using Helm

---

## Prerequisites

Before using the CI/CD pipeline, ensure:

- ✅ GitLab CE running (http://gitlab.local:8080)
- ✅ GitLab Runner registered and active
- ✅ Local Docker registry deployed to Kind
- ✅ Kind cluster accessible from Runner
- ✅ CI/CD variables configured in GitLab

---

## GitLab CI/CD Variables Setup

Navigate to: `Project → Settings → CI/CD → Variables`

### Required Variables

| Variable | Value | Masked | Protected |
|----------|-------|--------|-----------|
| `DB_URL` | `jdbc:postgresql://...neon.tech/neondb?sslmode=require` | ❌ | ✅ |
| `DB_USERNAME` | `neondb_owner` | ❌ | ✅ |
| `DB_PASSWORD` | `your-password` | ✅ | ✅ |
| `KUBECONFIG_CONTENT` | `<base64-encoded-kubeconfig>` | ❌ | ✅ |

### How to Get KUBECONFIG_CONTENT

```powershell
# Get kubeconfig
kind get kubeconfig --name app-cluster > kind-config.yaml

# Base64 encode (PowerShell)
$content = Get-Content -Raw kind-config.yaml
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($content))

# Copy the output and paste into GitLab CI variable
```

---

## Pipeline Triggers

### Automatic Triggers

The pipeline runs automatically when you push code to specific paths:

```yaml
# Triggers data-service pipeline
git commit -m "Fix data service bug"
git push gitlab main
# Runs: build → test → docker → deploy (manual)

# Triggers file-service pipeline  
git commit -m "Add file upload validation"
git push gitlab main
# Runs: build:file-service → test:file-service → docker:file-service

# Triggers UI pipeline
cd ui/
# ...make changes...
git commit -m "Update UI components"
git push gitlab main
# Runs: build:ui → test:ui → docker:ui
```

### Manual Triggers

1. Go to: `Project → CI/CD → Pipelines`
2. Click **"Run Pipeline"**
3. Select branch
4. Click **"Run pipeline"**

---

## Pipeline Stages Explained

### Stage 1: Build

**Purpose**: Compile source code, create artifacts

**Jobs**:
- `build:data-service` - Maven clean package
- `build:file-service` - Maven clean package
- `build:refdata-service` - Maven clean package
- `build:search-service` - Maven clean package
- `build:ui` - npm ci && npm run build

**Duration**: 3-5 minutes per service

**Artifacts**: JAR files (backend), build/ folder (UI)

**Example Output**:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  02:34 min
[INFO] Artifact created: dataservice-0.0.1-SNAPSHOT.jar
```

---

### Stage 2: Test

**Purpose**: Run unit tests, generate coverage

**Jobs**:
- `test:data-service` - Maven test
- `test:file-service` - Maven test  
- `test:refdata-service` - Maven test
- `test:search-service` - Maven test
- `test:ui` - npm test

**Duration**: 1-3 minutes per service

**Artifacts**: Test reports (JUnit XML), coverage reports

**Coverage Reporting**:
```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
Coverage: 78%
```

---

### Stage 3: Docker

**Purpose**: Build Docker images, push to registry

**Jobs**:
- `docker:data-service`
- `docker:file-service`
- `docker:refdata-service`
- `docker:search-service`
- `docker:ui`

**Process**:
```bash
# Build with commit SHA tag
docker build -t localhost:30500/app-data-service:abc1234 ./data

# Build with latest tag
docker build -t localhost:30500/app-data-service:latest ./data

# Push both tags
docker push localhost:30500/app-data-service:abc1234
docker push localhost:30500/app-data-service:latest
```

**Duration**: 2-4 minutes per service

**Tags Created**:
- `{service}:latest` - Always latest build
- `{service}:{commit-sha}` - Specific version for rollback

---

### Stage 4: Deploy

**Purpose**: Deploy to Kind cluster using Helm

**Job**: `deploy:kind-cluster`

**When**: Manual approval required (for `main` branch)

**Process**:
```bash
helm upgrade --install app ./infra/helm/app-chart \
  -f ./infra/helm/app-chart/values-local.yaml \
  --set database.url=$DB_URL \
  --set database.username=$DB_USERNAME \
  --set database.password=$DB_PASSWORD \
  --set dataService.image.tag=$CI_COMMIT_SHORT_SHA \
  --wait --timeout 5m
```

**Duration**: 2-3 minutes

**Verification**:
```
Release "app" has been upgraded. Happy Helming!
NAME: app
STATUS: deployed
REVISION: 5
```

---

## Pipeline Workflow Examples

### Example 1: Fix Bug in data-service

```bash
# 1. Create feature branch
git checkout -b fix/data-service-bug

# 2. Fix bug in data/src/...
# 3. Commit changes
git add data/
git commit -m "Fix null pointer in case creation"

# 4. Push toGitLab
git push gitlab fix/data-service-bug

# 5. Pipeline runs automatically:
#    ✓ build:data-service (3 min)
#    ✓ test:data-service (2 min)
#    ✓ docker:data-service (3 min)
#    ⏸ deploy (waiting for manual approval)

# 6. Review pipeline in GitLab
# 7. Click "Play" on deploy job
# 8. Deployment completes, verify in Kind cluster
kubectl get pods  # New pod with updated image
```

### Example 2: Update UI Component

```bash
# 1. Make changes to React component
cd ui/src/components/
# ... edit CaseList.jsx ...

# 2. Test locally
npm start  # Verify changes

# 3. Commit and push
git add ui/
git commit -m "Update CaseList component styling"
git push gitlab main

# 4. Pipeline runs:
#    ✓ build:ui (npm ci && build)
#    ✓ test:ui (npm test)
#    ✓ docker:ui (build & push image)
#    ⏸ deploy (manual approval)

# 5. Approve deployment
# 6. UI pod restarts with new image
```

### Example 3: Multi-Service Update

```bash
# Changes affect both data-service and file-service
git add data/ file/
git commit -m "Add new file metadata fields"
git push gitlab main

# Pipeline runs in parallel:
#    ✓ build:data-service & build:file-service
#    ✓ test:data-service & test:file-service
#    ✓ docker:data-service & docker:file-service
#    ⏸ deploy (deploys both services)
```

---

## Viewing Pipeline Results

### In GitLab UI

1. **Pipelines List**: `Project → CI/CD → Pipelines`
   - See all pipeline runs
   - Status: ✅ passed, ❌ failed, ⏸ manual, ⏳ running

2. **Pipeline Details**: Click on pipeline #
   - View all stages and jobs
   - Download artifacts
   - View logs

3. **Job Logs**: Click on job name
   - Real-time log streaming
   - Search logs
   - Download raw logs

### Via GitLab API

```bash
# Get latest pipeline status
curl -H "PRIVATE-TOKEN: your-token" \
  "http://gitlab.local:8080/api/v4/projects/1/pipelines"

# Get specific pipeline
curl -H "PRIVATE-TOKEN: your-token" \
  "http://gitlab.local:8080/api/v4/projects/1/pipelines/123"
```

---

## Troubleshooting Pipeline Issues

### Issue 1: Build Fails - Maven Dependencies

**Error**:
```
[ERROR] Failed to execute goal on project dataservice: 
Could not resolve dependencies
```

**Solution**:
```yaml
# In .gitlab-ci.yml, verify Maven cache is configured:
cache:
  key: "${CI_JOB_NAME}"
  paths:
    - .m2/repository
```

### Issue 2: Docker Push Fails

**Error**:
```
error pushing image: connection refused
```

**Solutions**:
1. Verify registry is running:
   ```bash
   kubectl get pods -n container-registry
   ```

2. Check runner can access registry:
   ```bash
   docker exec gitlab-runner curl http://localhost:30500/v2/
   ```

3. Verify network mode in runner config:
   ```yaml
   # docker-compose-runner.yml
   network_mode: "host"  # Or add to kind network
   ```

### Issue 3: Deployment Fails - Kubeconfig Invalid

**Error**:
```
error: unable to connect to cluster
```

**Solution**:
1. Regenerate kubeconfig:
   ```bash
   kind get kubeconfig --name app-cluster > kind-config.yaml
   ```

2. Re-encode as base64:
   ```powershell
   [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Get-Content -Raw kind-config.yaml)))
   ```

3. Update `KUBECONFIG_CONTENT` variable in GitLab

### Issue 4: Tests Fail

**Error**:
```
Tests run: 45, Failures: 3, Errors: 0
```

**Solution**:
1. View test logs in GitLab job
2. Fix failing tests locally:
   ```bash
   cd data
   mvn test  # Run tests locally
   ```
3. Commit fixes and push

### Issue 5: Helm Upgrade Fails

**Error**:
```
Error: UPGRADE FAILED: timed out waiting for the condition
```

**Solutions**:
1. Check pod status:
   ```bash
   kubectl get pods
   kubectl describe pod <pod-name>
   ```

2. Increase timeout in `.gitlab-ci.yml`:
   ```yaml
   --timeout 10m  # Increase from 5m
   ```

3. Check resource limits in `values.yaml`

---

## Pipeline Optimization

### Speed Up Builds

1. **Use Cached Dependencies**:
   ```yaml
   cache:
     paths:
       - .m2/repository  # Maven
       - ui/node_modules/  # npm
   ```

2. **Parallel Builds**:
   - Jobs run in parallel automatically
   - Add more runners for more parallelism

3. **Skip Unnecessary Jobs**:
   ```yaml
   only:
     changes:
       - data/**/*  # Only run when data/ changes
   ```

### Reduce Docker Build Time

1. **Use Multi-Stage Builds**:
   ```dockerfile
   FROM maven:3.8-openjdk-21 AS build
   WORKDIR /app
   COPY pom.xml .
   RUN mvn dependency:go-offline  # Cache deps
   
   COPY src src
   RUN mvn package -DskipTests
   
   FROM openjdk:21-jre-slim
   COPY --from=build /app/target/*.jar app.jar
   CMD ["java", "-jar", "app.jar"]
   ```

2. **Layer Caching**:
   - Put less frequently changing layers first
   - Dependencies layer separate from source code

---

## Best Practices

### Branching Strategy

```
main (protected)
  ├── develop (auto-deploy)
  ├── feature/new-feature (manual deploy)
  └── hotfix/critical-bug (manual deploy)
```

**Rules**:
- `main`: Production-ready, manual deploy, requires code review
- `develop`: Auto-deploy to dev environment
- `feature/*`: Manual deploy, requires tests to pass
- `hotfix/*`: Manual deploy, urgent fixes

### Commit Messages

```bash
# Good
git commit -m "feat(data-service): Add case validation"
git commit -m "fix(ui): Resolve file upload timeout"
git commit -m "refactor(file-service): Extract file validator"

# Bad
git commit -m "fixed stuff"
git commit -m "updates"
```

### Code Review

1. Create merge request in GitLab
2. Pipeline must pass before merge
3. Require 1 approval
4. Squash commits on merge

---

## Monitoring Pipeline Health

### Metrics to Track

- Pipeline success rate
- Average pipeline duration
- Failed jobs by type
- Deployment frequency

### Alerts

Set up GitLab notifications:
- Pipeline failures
- Deployment successes
- Security scan alerts

---

## Next Steps

1. ✅ Pipeline configured and running
2. ➡️ Add security scanning (Trivy, SAST)
3. ➡️ Add performance testing stage
4. ➡️ Add automated rollback on failure
5. ➡️ Set up staging environment pipeline
6. ➡️ Configure production deployment pipeline

---

## Resources

- [GitLab CI/CD Documentation](https://docs.gitlab.com/ee/ci/)
- [Kubernetes Deployment Strategies](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [Helm Best Practices](https://helm.sh/docs/chart_best_practices/)
