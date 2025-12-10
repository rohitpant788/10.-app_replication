#!/bin/bash

# GitLab Runner Registration Script
# This script registers a GitLab Runner with your local GitLab instance

set -e

echo "========================================="
echo "GitLab Runner Registration"
echo "========================================="
echo ""

# Configuration
GITLAB_URL="http://gitlab.local:8080"
RUNNER_NAME="local-docker-runner"
RUNNER_EXECUTOR="docker"
DOCKER_IMAGE="docker:latest"

# Check if runner container is running
if ! docker ps | grep -q gitlab-runner; then
    echo "Error: gitlab-runner container is not running!"
    echo "Start it with: docker-compose -f docker-compose-runner.yml up -d"
    exit 1
fi

# Prompt for registration token
echo "To register this runner, you need a registration token from GitLab:"
echo "1. Go to http://gitlab.local:8080"
echo "2. Login as root (password: GitLab@2025Root)"
echo "3. Navigate to Admin Area > Runners > Register an instance runner"
echo "4. Copy the registration token"
echo ""
read -p "Enter GitLab registration token: " REGISTRATION_TOKEN

if [ -z "$REGISTRATION_TOKEN" ]; then
    echo "Error: Registration token cannot be empty!"
    exit 1
fi

# Register the runner
echo ""
echo "Registering runner..."
docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "${GITLAB_URL}" \
  --registration-token "${REGISTRATION_TOKEN}" \
  --executor "${RUNNER_EXECUTOR}" \
  --docker-image "${DOCKER_IMAGE}" \
  --description "${RUNNER_NAME}" \
  --docker-privileged \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
  --docker-volumes "/cache" \
  --docker-network-mode "gitlab-network"

echo ""
echo "========================================="
echo "Runner registered successfully!"
echo "========================================="
echo ""
echo "Verify runner status:"
echo "1. Go to http://gitlab.local:8080/admin/runners"
echo "2. You should see '${RUNNER_NAME}' listed"
echo ""
echo "To view runner logs:"
echo "  docker logs -f gitlab-runner"
echo ""
echo "To unregister runner:"
echo "  docker exec gitlab-runner gitlab-runner unregister --all-runners"
echo ""
