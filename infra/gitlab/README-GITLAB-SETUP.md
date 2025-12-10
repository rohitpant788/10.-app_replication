# GitLab CE Local Setup Guide

## Overview

This guide walks you through setting up GitLab Community Edition (CE) locally for CI/CD development. GitLab will run in Docker and integrate with your Kind Kubernetes cluster.

---

## Prerequisites

- Docker Desktop installed and running
- At least 8GB RAM available for GitLab
- 20GB free disk space
- Windows PowerShell or Git Bash

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Local Development Machine               │
│                                                           │
│  ┌──────────────┐      ┌────────────────┐               │
│  │  GitLab CE   │─────▶│ GitLab Runner  │               │
│  │  (Docker)    │      │   (Docker)     │               │
│  │  Port: 8080  │      │                │               │
│  └──────────────┘      └────────────────┘               │
│         │                      │                         │
│         │                      ▼                         │
│         │              ┌────────────────┐                │
│         └─────────────▶│  Kind Cluster  │                │
│                        │  + Registry    │                │
│                        └────────────────┘                │
└─────────────────────────────────────────────────────────┘
```

---

## Step 1: Update Hosts File

Add GitLab hostname to your hosts file:

**Windows**: `C:\Windows\System32\drivers\etc\hosts` (Run Notepad as Administrator)

Add this line:
```
127.0.0.1 gitlab.local
```

**Verify**:
```powershell
ping gitlab.local
# Should respond from 127.0.0.1
```

---

## Step 2: Create Docker Network

GitLab and Runner need to communicate:

```powershell
cd f:\10. app_replication\infra\gitlab
docker network create gitlab-network
```

---

## Step 3: Start GitLab CE

```powershell
# Start GitLab
docker-compose -f docker-compose-gitlab.yml up -d

# Monitor logs (this will take 3-5 minutes for first startup)
docker logs -f gitlab-ce
```

**Wait for**: `gitlab Reconfigured!` message in logs

**Initial startup time**: 3-5 minutes
**Subsequent startups**: 1-2 minutes

---

## Step 4: Access GitLab

1. **Open browser**: http://gitlab.local:8080

2. **First-time login**:
   - Username: `root`
   - Password: `GitLab@2025Root`

   ⚠️ **Change this password immediately after first login!**

3. **You should see**: GitLab dashboard

---

## Step 5: Create Your First Project

1. Click **"New project"** → **"Create blank project"**

2. **Project details**:
   - Project name: `app-replication`
   - Visibility: Private
   - Initialize with README: ✅ (checked)

3. Click **"Create project"**

---

## Step 6: Generate Personal Access Token

You'll need this for pushing code:

1. Click your avatar (top right) → **Settings**
2. Left sidebar → **Access Tokens**
3. Create token:
   - Name: `local-dev-token`
   - Expiration: 1 year from now
   - Scopes: 
     - ✅ api
     - ✅ read_repository
     - ✅ write_repository
4. Click **"Create personal access token"**
5. **Copy the token** - you won't see it again!

---

## Step 7: Push Your Code to GitLab

```powershell
cd f:\10. app_replication

# Add GitLab as remote
git remote add gitlab http://gitlab.local:8080/root/app-replication.git

# Push code
git push gitlab main
# Username: root
# Password: <your-personal-access-token>
```

---

## Step 8: Start GitLab Runner

```powershell
# Start runner
docker-compose -f docker-compose-runner.yml up -d

# Verify it's running
docker ps | findstr gitlab-runner
```

---

## Step 9: Register Runner

**For Windows PowerShell**, convert the bash script:

```powershell
# Get registration token from GitLab
# Go to: http://gitlab.local:8080/admin/runners
# Click "Register an instance runner"
# Copy the registration token

$REGISTRATION_TOKEN = "YOUR_TOKEN_HERE"

# Register runner
docker exec -it gitlab-runner gitlab-runner register `
  --non-interactive `
  --url "http://gitlab.local:8080" `
  --registration-token "$REGISTRATION_TOKEN" `
  --executor "docker" `
  --docker-image "docker:latest" `
  --description "local-docker-runner" `
  --docker-privileged `
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" `
  --docker-volumes "/cache" `
  --docker-network-mode "gitlab-network"
```

---

## Step 10: Verify Runner

1. Go to: http://gitlab.local:8080/admin/runners
2. You should see: **"local-docker-runner"** with green status

---

## Configuration Details

### GitLab Ports
- **8080**: Web UI (HTTP)
- **8443**: HTTPS (if configured)
- **2222**: SSH (for git operations)
- **5050**: Container Registry

### Data Persistence

All GitLab data is stored in Docker volumes:
```
gitlab-config: /etc/gitlab
gitlab-logs: /var/log/gitlab  
gitlab-data: /var/opt/gitlab
```

**To backup**:
```powershell
docker run --rm -v gitlab-data:/data -v ${PWD}:/backup ubuntu tar czf /backup/gitlab-backup.tar.gz /data
```

**To restore**:
```powershell
docker run --rm -v gitlab-data:/data -v ${PWD}:/backup ubuntu tar xzf /backup/gitlab-backup.tar.gz -C /
```

---

## Troubleshooting

### Issue 1: Port 8080 Already in Use

**Error**: `bind: address already in use`

**Fix**:
```powershell
# Find what's using port 8080
netstat -ano | findstr :8080

# Kill the process (replace <PID> with actual PID)
taskkill /PID <PID> /F

# Or change GitLab port in docker-compose-gitlab.yml:
ports:
  - '9090:8080'  # Changed to 9090
```

### Issue 2: GitLab Takes Too Long to Start

**Solution**: 
- GitLab requires 4GB+ RAM
- Close other applications
- Check Docker Desktop has sufficient resources allocated

### Issue 3: Can't Access gitlab.local

**Check**:
1. Hosts file entry exists
2. GitLab container is running: `docker ps | findstr gitlab`
3. Try direct IP: http://localhost:8080

### Issue 4: Runner Registration Fails

**Common causes**:
- Wrong registration token
- Gitlab-network doesn't exist
- GitLab not fully started

**Fix**:
```powershell
# Verify network
docker network ls | findstr gitlab

# Check GitLab health
docker exec gitlab-ce gitlab-rake gitlab:check
```

---

## Useful Commands

### Start/Stop GitLab

```powershell
# Start
cd f:\10. app_replication\infra\gitlab
docker-compose -f docker-compose-gitlab.yml up -d

# Stop
docker-compose -f docker-compose-gitlab.yml stop

# Stop and remove
docker-compose -f docker-compose-gitlab.yml down
```

### View Logs

```powershell
# GitLab logs
docker logs -f gitlab-ce

# Runner logs
docker logs -f gitlab-runner

# Last 100 lines
docker logs --tail 100 gitlab-ce
```

### Restart Services

```powershell
# Restart GitLab
docker restart gitlab-ce

# Restart Runner
docker restart gitlab-runner
```

### Access GitLab Rails Console

```powershell
docker exec -it gitlab-ce gitlab-rails console
```

---

## Next Steps

1. ✅ GitLab CE running
2. ✅ Runner registered
3. ➡️ **Next**: Set up `.gitlab-ci.yml` pipeline
4. ➡️ **Next**: Configure CI/CD variables
5. ➡️ **Next**: Deploy to Kind cluster

---

## Security Notes

### For Local Development Only!

⚠️ This setup is **NOT production-ready**:
- Default passwords used
- HTTP only (no TLS)
- Single container (not HA)
- No backup strategy

### For Production:

- Use strong passwords / LDAP
- Enable HTTPS with valid certificates
- Configure email notifications
- Set up automated backups
- Use external PostgreSQL
- Enable monitoring

---

## Resources

- **GitLab Docs**: https://docs.gitlab.com/ee/install/docker.html
- **Runner Docs**: https://docs.gitlab.com/runner/
- **CI/CD Tutorial**: https://docs.gitlab.com/ee/ci/

---

## Support

If you encounter issues:
1. Check logs: `docker logs gitlab-ce`
2. Verify network: `docker network inspect gitlab-network`
3. Check resources: Docker Desktop → Settings → Resources
4. Restart: `docker-compose -f docker-compose-gitlab.yml restart`
