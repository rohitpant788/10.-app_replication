# GitLab CI/CD Setup for Beginners - Complete Guide

> **For Novice Engineers**: This guide will teach you step-by-step how to set up automated deployments. No prior experience needed!

## Table of Contents

1. [What is CI/CD?](#what-is-cicd)
2. [Why We Need It](#why-we-need-it)
3. [How It Works](#how-it-works)
4. [Part 1: GitLab Setup](#part-1-gitlab-setup)
5. [Part 2: Runner Setup](#part-2-runner-setup)
6. [Part 3: Registry Setup](#part-3-registry-setup)
7. [Part 4: Configure CI Variables](#part-4-configure-ci-variables)
8. [Part 5: Your First Pipeline](#part-5-your-first-pipeline)
9. [Daily Workflow](#daily-workflow)
10. [Troubleshooting](#troubleshooting)

---

## What is CI/CD?

**Simple Explanation for Beginners**:

- **GitLab**: Like GitHub, but you can run it on your own computer. It stores your code.
- **CI (Continuous Integration)**: Automatically builds and tests your code when you make changes
- **CD (Continuous Deployment)**: Automatically deploys tested code to Kubernetes

**The Magic Flow**:
```
You type code → Push to GitLab → GitLab builds it → GitLab tests it → 
GitLab makes Docker images → GitLab deploys to Kubernetes → DONE!
```

**Think of it like**:
- You = Chef writing recipes
- GitLab = Restaurant manager
- Runner = Kitchen staff who cooks
- Registry = Storage for prepared meals
- Kubernetes = Dining area where food is served

---

## Why We Need It

**Without CI/CD (The Old Way - Manual)**:

1. You write code ✍️
2. You manually run `mvn clean package` (8 minutes) ⏰
3. You manually run `docker build` (5 minutes) ⏰
4. You manually run `docker push` (3 minutes) ⏰
5. You manually run `helm upgrade` (3 minutes) ⏰
6. **Total: 19 minutes + you doing boring repetitive work** 😩
7. Easy to make mistakes (forgot a step? Wrong command?) ❌

**With CI/CD (The Modern Way - Automatic)**:

1. You write code ✍️
2. You run `git push` ⏰
3. **Everything else happens automatically!** ✨
4. **Total: 2 minutes of your time, 10 minutes computer time** ✅
5. Consistent, no human errors ✅
6. You can go get coffee while it deploys ☕

---

## How It Works

### The Architecture

```
┌──────────────────────────────────────────────────────────────┐
│              YOUR COMPUTER (Local Development)                │
│                                                                │
│  ┌──────────────┐          ┌──────────────┐                  │
│  │  GitLab CE   │─────────▶│ GitLab Runner│                  │
│  │  Stores Code │          │ Does the Work│                  │
│  │  Port: 8080  │          │              │                  │
│  └──────┬───────┘          └──────┬───────┘                  │
│         │                         │                           │
│         │                         │                           │
│         └─────────┬───────────────┘                           │
│                   │                                            │
│                   ▼                                            │
│         ┌──────────────────┐        ┌──────────────┐         │
│         │ Docker Registry  │        │ Kind Cluster │         │
│         │ Stores Images    │        │ Runs Your App│         │
│         │ Port: 30500      │        │              │         │
│         └──────────────────┘        └──────────────┘         │
└──────────────────────────────────────────────────────────────┘
```

### The Pipeline Stages

When you push code, this happens automatically:

```
STAGE 1: BUILD 📦
→ Compiles your Java code with Maven
→ Builds your React app with npm
→ Takes: 3-5 minutes

STAGE 2: TEST 🧪
→ Runs all unit tests
→ Makes sure nothing is broken
→ Takes: 2-3 minutes

STAGE 3: DOCKER 🐳
→ Creates Docker images
→ Pushes them to local registry
→ Takes: 3-4 minutes

STAGE 4: DEPLOY 🚀
→ Waits for your approval (safety!)
→ Uses Helm to update Kubernetes
→ Takes: 2-3 minutes
```

---

## Part 1: GitLab Setup

### Step 1: Prepare Your Computer

**1.1 - Edit Hosts File**

This tells Windows where to find GitLab:

1. Press Windows key
2. Type "Notepad"
3. Right-click "Notepad" → **Run as Administrator**
4. File → Open → Navigate to: `C:\Windows\System32\drivers\etc\hosts`
5. At the bottom, add this line:
   ```
   127.0.0.1 gitlab.local
   ```
6. Save (Ctrl+S) and close

**Test it worked**:
```powershell
ping gitlab.local
# Should show: Reply from 127.0.0.1
```

---

**1.2 - Create Docker Network**

Open PowerShell:
```powershell
docker network create gitlab-network
```

You should see:
```
Successfully created network gitlab-network
```

---

### Step 2: Start GitLab

```powershell
# Go to GitLab folder
cd f:\10. app_replication\infra\gitlab

# Start GitLab
docker-compose -f docker-compose-gitlab.yml up -d

# You'll see:
# Creating gitlab-ce ... done
```

**Watch It Start Up**:
```powershell
docker logs -f gitlab-ce
```

You'll see LOTS of text scrolling. **This is normal!** ✅

**Wait for this message** (takes 3-5 minutes first time):
```
gitlab Reconfigured!
```

When you see that, press **Ctrl+C** to stop watching.

---

### Step 3: Access GitLab

1. Open your web browser
2. Go to: **http://gitlab.local:8080**
3. You should see a login page! 🎉

**First Time Login**:
- Username: `root`
- Password: `GitLab@2025Root`

⚠️ **IMPORTANT**: After logging in:
1. Click your avatar (top right)
2. Click "Edit profile"
3. Click "Password" in left sidebar
4. Change to a password you'll remember!

---

### Step 4: Create Your Project

1. Click the blue **"New project"** button
2. Click **"Create blank project"**
3. Fill in the form:
   - **Project name**: `app-replication`
   - **Project URL**: Leave as is
   - **Visibility Level**: Keep "Private"
   - **Initialize repository with a README**: ✅ **Check this box!**
4. Click **"Create project"**

Success! You have a GitLab project! 🎊

---

###Step 5: Push Your Code to GitLab

Now let's get your code into GitLab:

```powershell
# Go to your project
cd f:\10. app_replication

# Add GitLab as a "remote" (a place to push code)
git remote add gitlab http://gitlab.local:8080/root/app-replication.git

# Try to push
git push gitlab main
```

**You'll be asked**:
```
Username for 'http://gitlab.local:8080': root
Password for 'http://gitlab.local:8080': (type the password you set)
```

**Success message**:
```
To http://gitlab.local:8080/root/app-replication.git
 * [new branch]      main -> main
```

**Verify**: Refresh GitLab in browser - you should see all your files! ✨

---

## Part 2: Runner Setup

**What is a Runner?**  
Think of it as a robot worker. When you push code, the Runner actually does the building, testing, and deploying.

### Step 1: Start the Runner

```powershell
# Make sure you're in the gitlab folder
cd f:\10. app_replication\infra\gitlab

# Start the runner
docker-compose -f docker-compose-runner.yml up -d

# Verify it's running
docker ps | findstr runner
```

You should see a line with `gitlab-runner` in it. ✅

---

### Step 2: Get Registration Token

1. In GitLab, click the wrench icon (⚙️) in top-left → **"Admin Area"**
2. In left sidebar, click **"CI/CD"** → **"Runners"**
3. Click the blue button **"Register an instance runner"**
4. You'll see a **Registration token** like: `GR1348941abc...`
5. Click the "Copy" button 📋

---

### Step 3: Register the Runner

In PowerShell (replace `PASTE_TOKEN_HERE` with your actual token):

```powershell
$TOKEN = "PASTE_TOKEN_HERE"

docker exec -it gitlab-runner gitlab-runner register `
  --non-interactive `
  --url "http://gitlab.local:8080" `
  --registration-token "$TOKEN" `
  --executor "docker" `
  --docker-image "docker:latest" `
  --description "local-docker-runner" `
  --docker-privileged `
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" `
  --docker-volumes "/cache" `
  --docker-network-mode "gitlab-network"
```

**Success!** You should see:
```
Runner registered successfully
```

---

### Step 4: Verify Runner Is Active

1. Go back to GitLab → Admin Area → Runners
2. You should see **"local-docker-runner"** with a GREEN circle ✅

If you see a green circle, you're good! The Runner is ready to work.

---

## Part 3: Registry Setup

**What is the Registry?**  
A place to store Docker images on your computer (instead of Docker Hub online).

**Why?** Much faster! Pushing/pulling images locally takes seconds, not minutes.

### Step 1: Delete Old Cluster (If You Have One)

```powershell
kind delete cluster --name app-cluster
```

Don't worry, we'll create a better one!

---

### Step 2: Create Cluster with Registry

```powershell
kind create cluster --config f:\10. app_replication\infra\k8s\kind-config-with-registry.yaml --name app-cluster
```

**Takes**: ~2 minutes  
**You'll see**: Lots of "Creating cluster..." messages

**Success message**:
```
Set kubectl context to "kind-app-cluster"
```

---

### Step 3: Install Ingress Controller

```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

Wait a bit, then check:
```powershell
kubectl wait --namespace ingress-nginx `
  --for=condition=ready pod `
  --selector=app.kubernetes.io/component=controller `
  --timeout=90s
```

---

### Step 4: Deploy Registry

```powershell
kubectl apply -f f:\10. app_replication\infra\registry\registry-deployment.yaml
```

**Wait for it to be ready**:
```powershell
kubectl get pods -n container-registry -w
```

Watch until you see:
```
NAME                        READY   STATUS    RESTARTS   AGE
registry-xxxxx              1/1     Running   0          45s
```

Press **Ctrl+C** to stop watching.

---

### Step 5: Test Registry

```powershell
curl http://localhost:30500/v2/_catalog
```

**Expected response**:
```json
{"repositories":[]}
```

Perfect! Empty list = registry is working! ✅

---

## Part 4: Configure CI Variables

**What are these?**  
Secret values (like passwords) that GitLab uses but doesn't show in logs.

### Step 1: Navigate to CI/CD Settings

1. In GitLab, go to your project: `app-replication`
2. Left sidebar → **Settings** → **CI/CD**
3. Find "Variables" section → Click **"Expand"**

---

### Step 2: Add Database Variables

Click **"Add variable"** and create these one by one:

**Variable 1: DB_URL**
- **Key**: `DB_URL`
- **Value**: `jdbc:postgresql://ep-steep-paper-a4w8wloy-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require`
  (Use YOUR actual Neon database URL!)
- **Type**: Variable
- **Flags**: 
  - Protect variable: ✅
  - Mask variable: ❌

Click **"Add variable"**

**Variable 2: DB_USERNAME**
- **Key**: `DB_USERNAME`
- **Value**: `neondb_owner` (or your username)
- **Protect variable**: ✅
- **Mask variable**: ❌

**Variable 3: DB_PASSWORD**
- **Key**: `DB_PASSWORD`
- **Value**: (your actual password)
- **Protect variable**: ✅
- **Mask variable**: ✅ (Important! Hides it in logs)

---

### Step 3: Add Kubernetes Config

This is the trickiest one, but follow these steps carefully:

**3.1 - Get the Kubeconfig**:
```powershell
kind get kubeconfig --name app-cluster > C:\temp\kind-config.yaml
```

**3.2 - Convert to Base64**:
```powershell
$content = Get-Content -Raw C:\temp\kind-config.yaml
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($content))
echo $base64
```

**3.3 - Copy the LONG string that appears**

**3.4 - In GitLab, add variable**:
- **Key**: `KUBECONFIG_CONTENT`
- **Value**: (paste the long base64 string)
- **Protect variable**: ✅
- **Mask variable**: ❌ (too long to mask)

Click **"Add variable"**

---

**Verify**: You should now have 4 variables:
- DB_URL
- DB_USERNAME  
- DB_PASSWORD
- KUBECONFIG_CONTENT

---

## Part 5: Your First Pipeline!

Time to see the magic! Let's make a small change and watch GitLab do all the work.

### Step 1: Make a Test Change

```powershell
# Open the data service
cd f:\10. app_replication\data\src\main\java\com\app\dataservice

# Open DataController.java in any text editor
# Add a comment at the top: // Testing CI/CD pipeline
# Save the file
```

---

### Step 2: Commit and Push

```powershell
cd f:\10. app_replication
git add .
git commit -m "test: First CI/CD pipeline run"
git push gitlab main
```

---

### Step 3: Watch the Pipeline!

1. Go to GitLab: http://gitlab.local:8080/root/app-replication
2. Left sidebar → **CI/CD** → **Pipelines**
3. You should see a pipeline starting! 🎉

**What you'll see**:

```
Pipeline #1 - Running
├─ build:data-service (running) ⏳
├─ test:data-service (pending) ⏸
├─ docker:data-service (pending) ⏸
└─ deploy (pending) ⏸
```

Click on the pipeline number to see details!

---

### Step 4: Watch Each Stage

**Stage 1 - BUILD** (Running):
- Click on `build:data-service`
- You'll see live logs of Maven compiling your code
- Takes ~3 minutes
- Ends with: `BUILD SUCCESS` ✅

**Stage 2 - TEST** (Starts automatically):
- Runs your unit tests
- You see test results
- Takes ~2 minutes
- Ends with: `Tests run: XX, Failures: 0` ✅

**Stage 3 - DOCKER** (Starts automatically):
- Builds Docker image
- Pushes to localhost:30500
- Takes ~3 minutes
- Ends with: `Successfully pushed` ✅

**Stage 4 - DEPLOY** (Waits for you!):
- Shows a ▶️ Play button
- This is WAITING for your approval
- Don't click it yet! Let's understand what it does.

---

### Step 5: Approve and Deploy

The deploy stage is waiting because we want to be careful - we're deploying to "production" (even though it's local).

**To deploy**:
1. Click the green ▶️ **Play** button next to `deploy:kind-cluster`
2. It starts running
3. You'll see Helm commands executing
4. Takes ~2 minutes
5. Ends with: `Release "app" has been upgraded` ✅

---

### Step 6: Verify Deployment

```powershell
# Check pods
kubectl get pods

# You should see pods with recent AGE (like 2m)
# This means they just restarted with your new code!

# Check the app
curl http://app.local/data/cases/next-id
```

**CONGRATULATIONS!** 🎉 You just completed your first automated deployment!

---

## Daily Workflow

Now that it's set up, here's your daily routine:

### Morning: Start Everything

```powershell
# Check if GitLab is running
docker ps | findstr gitlab

# If not, start it
docker start gitlab-ce gitlab-runner

# Verify cluster
kubectl get pods
```

---

### During Development

```powershell
# 1. Create a feature branch (good practice!)
git checkout -b feature/my-new-feature

# 2. Make your code changes
# ... edit files ...

# 3. Test locally (optional but recommended)
cd data
mvn test

# 4. Commit your changes
cd f:\10. app_replication
git add .
git commit -m "feat: describe what you did"

# 5. Push to GitLab
git push gitlab feature/my-new-feature

# 6. Go to GitLab and watch the pipeline
# 7. If everything passes, you can merge to main later
```

---

### End of Day (Optional Resource Saving)

```powershell
# Stop GitLab if you want to save RAM/CPU
docker stop gitlab-ce gitlab-runner

# Cluster keeps running (it uses minimal resources)
```

---

## Troubleshooting

### Problem 1: Can't Access gitlab.local:8080

**Symptom**: Browser shows "Can't reach this page"

**Checks**:
```powershell
# Is GitLab running?
docker ps | findstr gitlab

# Can you ping it?
ping gitlab.local
```

**Solutions**:
```powershell
# Solution 1: Restart GitLab
docker restart gitlab-ce
# Wait 2-3 minutes for startup

# Solution 2: Try localhost instead
# Browser: http://localhost:8080

# Solution 3: Check hosts file again
# Make sure 127.0.0.1 gitlab.local is there
```

---

### Problem 2: Pipeline Stuck in "Pending"

**Symptom**: Jobs show gray circle, never start

**Check**:
```powershell
docker logs gitlab-runner
```

**Solution**:
```powershell
# Runner probably not registered correctly
# See if runner shows in GitLab → Admin → Runners
# If red or not there, register again (see Part 2)
```

---

### Problem 3: Build Fails - "Cannot Resolve Dependencies"

**Symptom**: Maven build fails in pipeline

**Common causes**:
- Internet connection issue
- Maven cache corrupted

**Solution**:
```powershell
# Run locally first to download dependencies
cd data
mvn clean install

# Then commit and push again
```

---

### Problem 4: Docker Stage Fails - "Cannot Connect to Docker Daemon"

**Symptom**: Docker build fails in pipeline

**Solution**:
```powershell
# Restart the runner
docker restart gitlab-runner

# Verify runner can access Docker
docker exec gitlab-runner docker ps
```

---

### Problem 5: Deploy Fails - "Unable to Connect to Cluster"

**Symptom**: Deploy job shows connection error

**Most Common Cause**: KUBECONFIG variable is wrong or outdated

**Solution**:
```powershell
# Regenerate kubeconfig
kind get kubeconfig --name app-cluster > C:\temp\kind-config.yaml

# Convert to base64 again
$content = Get-Content -Raw C:\temp\kind-config.yaml
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($content))
echo $base64

# Update KUBECONFIG_CONTENT variable in GitLab
# Settings → CI/CD → Variables → Edit KUBECONFIG_CONTENT
```

---

### Problem 6: "Image Not Found" Error

**Symptom**: Pods can't pull images from localhost:30500

**Check**:
```powershell
# Is registry running?
kubectl get pods -n container-registry

# Are images in registry?
curl http://localhost:30500/v2/_catalog
```

**Solution**:
```powershell
# If registry not running:
kubectl delete pod -n container-registry -l app=registry

# Wait for new pod
kubectl get pods -n container-registry -w
```

---

## Learning Resources

### For Complete Beginners

**Git Basics**:
- What is Git? https://git-scm.com/book/en/v2/Getting-Started-What-is-Git%3F
- Basic Git Commands: https://git-scm.com/docs/gittutorial

**Docker Basics**:
- What is Docker? https://docs.docker.com/get-started/overview/
- Docker Tutorial: https://docs.docker.com/get-started/

**Kubernetes Basics**:
- What is Kubernetes? https://kubernetes.io/docs/concepts/overview/what-is-kubernetes/
- Basic Tutorial: https://kubernetes.io/docs/tutorials/kubernetes-basics/

### CI/CD Learning

**GitLab CI/CD**:
- Quick Start: https://docs.gitlab.com/ee/ci/quick_start/  
- YAML Reference: https://docs.gitlab.com/ee/ci/yaml/
- Pipeline Examples: https://docs.gitlab.com/ee/ci/examples/

### Project-Specific Docs

In this project, you can find more detailed guides:
- **GitLab Deep Dive**: `infra/gitlab/README-GITLAB-SETUP.md`
- **Registry Setup**: `infra/registry/README-REGISTRY-SETUP.md`
- **Pipeline Details**: `docs/ci-cd/pipeline-guide.md`
- **Architecture**: `docs/architecture/system-overview.md`

---

## Summary

### What You've Accomplished ✨

✅ **GitLab CE**: Running on your computer (like your own GitHub!)  
✅ **GitLab Runner**: Automated worker ready to build your code  
✅ **Docker Registry**: Local image storage for fast deployments  
✅ **CI/CD Pipeline**: Automatic build → test → deploy  
✅ **Kubernetes**: Apps deploying automatically!

### Time Savings

**Before**: 20 minutes of manual work every deploy  
**After**: 2 minutes (just git push!) ⚡

### What Happens Automatically Now

1. Code compilation ✅
2. Running tests ✅
3. Building Docker images ✅
4. Pushing to registry ✅
5. Deploying to Kubernetes ✅
6. Verifying deployment ✅

### Next Steps

1. ✅ Try changing different services (UI, file-service)
2. ✅ Watch how pipeline only builds what changed
3. ✅ Create feature branches for your work
4. ✅ Get comfortable with the workflow
5. ✅ Learn about merge requests for team collaboration

---

## Need Help?

**If you get stuck**:
1. Check this guide's troubleshooting section
2. Look at the logs: Everything has logs!
   - GitLab: `docker logs gitlab-ce`
   - Runner: `docker logs gitlab-runner`
   - Registry: `kubectl logs -n container-registry deployment/registry`
   - Pipelines: Click on job in GitLab to see logs
3. Check the detailed guides in `/docs/` folder

**Remember**: Everyone struggles at first! CI/CD is complex, but once you get it, it saves so much time! 🚀

---

_Last Updated: December 2025_  
_Written for absolute beginners - if anything is unclear, please ask!_
