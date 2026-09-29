# Task Manager: End-to-End DevOps Pipeline

Capstone project for **DevOps & Automation Lab (ENSP461)**: a Spring Boot + PostgreSQL task manager, taken through Git → Jenkins → Docker → Kubernetes → Ansible.

## Stack

| Layer | Tech |
|---|---|
| App | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Actuator) |
| Database | PostgreSQL (H2 in-memory for tests) |
| UI | Single responsive page (`src/main/resources/static/index.html`) calling the REST API |
| Build | Maven wrapper (`./mvnw`), no local Maven install needed |

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
```
