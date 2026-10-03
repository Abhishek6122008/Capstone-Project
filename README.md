# Task Manager: End-to-End DevOps Pipeline

Capstone project for **DevOps & Automation Lab (ENSP461)**: a Spring Boot + PostgreSQL task manager, taken through Git → Jenkins → Docker → Kubernetes → Ansible.

## Stack

| Layer | Tech |
|---|---|
| App | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Database | PostgreSQL (H2 in-memory for tests) |
| UI | Single responsive page (`src/main/resources/static/index.html`) calling the REST API |
| Build | Maven wrapper (`./mvnw`), no local Maven install needed |
| CI | Jenkins (runs in Docker), pushes images to Docker Hub |
| Containers | Docker multi-stage build, Docker Compose |
| Orchestration | Kubernetes on Minikube, manifests managed with kustomize |
| Automation | Ansible playbooks: Docker, Kubernetes, host config, deployment |

## Features

- **Login**: form login at `/login` for browsers, HTTP Basic for API clients. Users live in the `app_user` table and passwords are BCrypt-hashed. An admin account is created on first start from `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
- **CRUD REST API** on `/api/tasks`, with validation.
- **Error handling**: 400/404 responses come back as RFC 7807 JSON (`{"status":404,"detail":"Task 9 not found",...}`).
- **CSRF protection** for browser sessions.
- **Health endpoint** `/actuator/health` (public), used later by Docker and Kubernetes probes.

## Run locally

Requires JDK 21+ and Docker (for PostgreSQL).

```bash
# 1. Start PostgreSQL
docker run -d --name taskdb -p 5432:5432 \
  -e POSTGRES_DB=taskdb -e POSTGRES_USER=taskuser -e POSTGRES_PASSWORD=taskpass \
  postgres:17-alpine

# 2. Run the app (Windows: mvnw.cmd spring-boot:run)
./mvnw spring-boot:run

# 3. Open http://localhost:8080 and log in as admin / admin123
```

Run the tests (no database needed):

```bash
./mvnw test
```

## Configuration

Every setting can be overridden with an environment variable, so the same jar runs locally, in Docker Compose and in Kubernetes.

| Env var | Default |
|---|---|
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/taskdb` |
| `DB_USERNAME` | `taskuser` |
| `DB_PASSWORD` | `taskpass` |
| `ADMIN_USERNAME` | `admin` |
| `ADMIN_PASSWORD` | `admin123` |

## REST API

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/tasks` | – | 200 list |
| GET | `/api/tasks/{id}` | – | 200 / 404 |
| POST | `/api/tasks` | `{"title","description","done"}` | 201 |
| PUT | `/api/tasks/{id}` | `{"title","description","done"}` | 200 / 404 |
| DELETE | `/api/tasks/{id}` | – | 204 / 404 |

`title` is required (max 100 characters). `description` is optional (max 500).

```bash
curl -u admin:admin123 http://localhost:8080/api/tasks
curl -u admin:admin123 -H 'Content-Type: application/json' \
     -d '{"title":"Write Dockerfile"}' http://localhost:8080/api/tasks
curl -u admin:admin123 -X PUT -H 'Content-Type: application/json' \
     -d '{"title":"Write Dockerfile","done":true}' http://localhost:8080/api/tasks/1
curl -u admin:admin123 -X DELETE http://localhost:8080/api/tasks/1
```

## Docker

```bash
docker build -t abhishek6122008/taskmanager:local .   # multi-stage: JDK builds, JRE runs
docker compose up -d --build                          # app + PostgreSQL
docker compose ps                                     # both "healthy"
docker compose logs -f app
docker compose down                                   # add -v to also delete the DB volume
```

The image runs as a non-root user (uid 10001) on a JRE-only Alpine base, has a `HEALTHCHECK` on `/actuator/health`, and sizes the JVM heap from the container memory limit.

## Jenkins CI

The pipeline ([Jenkinsfile](Jenkinsfile)) runs **Checkout → Build & Test → Package → Docker Build → Push**. Each build pushes `abhishek6122008/taskmanager:<build number>` and `:latest`.

**1. Start Jenkins** (on the Linux box):

```bash
cd jenkins
DOCKER_GID=$(getent group docker | cut -d: -f3) docker compose up -d --build
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Open `http://<linux-box>:8081`, paste the password, choose **Select plugins → None** (the needed plugins are pre-installed), and create the admin user.

**2. Add the Docker Hub credential:** *Manage Jenkins → Credentials → System → Global → Add Credentials*
- Kind: **Username with password**
- Username: your Docker Hub username, Password: a Docker Hub **access token** (Account settings → Personal access tokens)
- ID: `dockerhub`

**3. Create the job:** *New Item → `taskmanager` → Pipeline*
- Definition: **Pipeline script from SCM**, SCM: **Git**
- Repository URL: `https://github.com/Abhishek6122008/Capstone-Project.git`, Branch: `*/main`, Script Path: `Jenkinsfile`
- Click **Build Now** once so Jenkins registers the triggers from the Jenkinsfile.

**4. Build on every push:**
- GitHub webhook (instant): repo *Settings → Webhooks → Add webhook*, Payload URL `http://<public-jenkins-url>/github-webhook/`, content type `application/json`. If Jenkins is on a home network, expose it with a tunnel such as `ngrok http 8081`.
- Without a webhook, the `pollSCM` trigger still picks up every push within 5 minutes.

## Kubernetes

Manifests are in [k8s/](k8s/) and deployed with kustomize:

| File | What it creates |
|---|---|
| `namespace.yaml` | `taskmanager` namespace |
| `configmap.yaml` | `DB_URL`, `POSTGRES_DB` |
| `kustomization.yaml` | Secret generated from `k8s/secret.env` (gitignored), image tag |
| `postgres-storage.yaml` | PersistentVolume (1Gi hostPath) + PersistentVolumeClaim |
| `postgres.yaml` | PostgreSQL Deployment + ClusterIP Service |
| `deployment.yaml` | App Deployment: 2 replicas, rolling update (maxSurge 1, maxUnavailable 0), probes, non-root |
| `service.yaml` | NodePort Service on port 30080 |
| `hpa.yaml` | HorizontalPodAutoscaler: 2–5 pods at 70% CPU |

Deploy by hand (the Ansible deploy playbook does the same thing):

```bash
minikube start --driver=docker --cpus=2 --memory=4096
minikube addons enable metrics-server
minikube image build -t docker.io/abhishek6122008/taskmanager:1.0 .   # build straight into Minikube
cp k8s/secret.env.example k8s/secret.env                              # then edit the passwords
kubectl apply -k k8s/
kubectl -n taskmanager get all,pv,pvc,configmap,secret,hpa
minikube service taskmanager -n taskmanager --url                     # app URL
```

### Scaling

```bash
kubectl -n taskmanager scale deployment/taskmanager --replicas=4
kubectl -n taskmanager get pods -w
kubectl -n taskmanager get hpa          # the HPA keeps replicas between 2 and 5 based on CPU
```

### Rolling update

```bash
minikube image build -t docker.io/abhishek6122008/taskmanager:1.1 .
kubectl -n taskmanager set image deployment/taskmanager app=docker.io/abhishek6122008/taskmanager:1.1
kubectl -n taskmanager annotate deployment/taskmanager kubernetes.io/change-cause="Release 1.1"
kubectl -n taskmanager rollout status deployment/taskmanager      # pods replaced one at a time
kubectl -n taskmanager rollout history deployment/taskmanager
```

### Rollback

```bash
# Ship a broken release: the new pod can't start, but maxUnavailable=0 keeps the old pods serving
kubectl -n taskmanager set image deployment/taskmanager app=docker.io/abhishek6122008/taskmanager:broken
kubectl -n taskmanager get pods                                   # one pod in ErrImagePull, the rest Running
kubectl -n taskmanager rollout undo deployment/taskmanager        # back to the previous revision (or --to-revision=N)
kubectl -n taskmanager rollout status deployment/taskmanager
```

### Persistence check

```bash
kubectl -n taskmanager delete pod -l app=postgres   # tasks are still there after the new pod starts
```

## Ansible

Playbooks are in [ansible/](ansible/) and run on the Linux box against `localhost` (see `inventory.ini` to target another machine over SSH). Settings live in `group_vars/all.yml`.

| Playbook | Does |
|---|---|
| `docker.yml` | Installs Docker Engine from Docker's apt repo (skipped if Docker is present), adds you to the `docker` group |
| `config.yml` | Configuration management: base packages, inotify sysctl limits, kubectl completion + `k` alias |
| `kubernetes.yml` | Installs kubectl + Minikube, starts the cluster, enables metrics-server |
| `deploy.yml` | Builds the image inside Minikube, writes the Secret from prompted passwords, applies `k8s/`, waits for rollout, prints the URL |
| `site.yml` | All of the above, in order |

```bash
cd ansible
ansible-playbook site.yml -K          # -K asks for your sudo password
ansible-playbook deploy.yml           # redeploy only
ansible-playbook config.yml -K --check --diff   # dry run: shows what would change
```

## Git workflow

- `main` always builds and passes tests.
- Each change is made on its own feature branch (`task-entity`, `task-crud-api`, …) as one focused commit.
- Branches are merged into `main` through a GitHub pull request (squash merge), so every PR number appears in the history.

```bash
git checkout main && git pull
git checkout -b my-feature
# ...edit...
git add -A && git commit -m "Short description"
git push -u origin my-feature
gh pr create --fill && gh pr merge --squash
```

## Project structure

```
src/main/java/com/capstone/taskmanager/
  TaskManagerApplication.java   entry point
  Task.java, TaskRepository.java            task entity + DB access
  TaskController.java                       /api/tasks CRUD
  AppUser.java, AppUserRepository.java      login users
  SecurityConfig.java                       login, CSRF, admin seeding
src/main/resources/
  application.properties        env-driven config
  static/index.html             UI
src/test/java/.../TaskApiTests.java         API tests (H2)
Dockerfile, .dockerignore       container image
docker-compose.yml              app + PostgreSQL
Jenkinsfile                     CI pipeline
jenkins/                        Jenkins server image + compose
k8s/                            Kubernetes manifests (kustomize)
ansible/                        Ansible playbooks
```
