# Docker environment

Docker Compose runs the Vue build behind Nginx, four Java 21 services, MySQL 8.4 and a single-node Kafka broker. Only the website is published on the host, at `http://127.0.0.1:8080`. Container DNS names are used for internal requests. The local MySQL installation and development ports remain separate.

## Start

Install and start Docker Desktop (or a Docker Engine with Compose v2). Allow enough memory for MySQL, Kafka and four JVMs; 8 GB is a practical starting allocation. Python 3 is needed only to create configuration.

From the project root:

```sh
python3 deploy/configure.py
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 600
```

Enter an administrator email and password when prompted. Database passwords are generated automatically. `.env` is private and excluded from source archives and Docker build contexts. The administrator is created on the first start; changing `.env` later does not reset an existing administrator. Register customer accounts through the website.

The first build downloads npm and Maven dependencies and runs backend tests. Open `http://127.0.0.1:8080`. Set `WEB_PORT` in `.env` if that port is occupied.

## Data

The empty MySQL volume is initialized with four schemas, four service accounts, and the public catalogue: 9 categories, 45 products and 179 checks with image metadata. The frontend image files are included. The catalogue SQL contains no customer accounts, feedback, private photos or QA fixtures. It retains the current published version of each public guide; it is not a backup of historical customer records or earlier guide versions.

MySQL, Kafka and private inspection photos each use named volumes. The initialization scripts run only for an empty MySQL data volume. Existing volumes are not reimported on restart. Service accounts have data permissions only on their own schema. Database and broker ports are not published.

```sh
docker compose ps
docker compose logs --tail=100 identity guide inspection report
docker compose stop
docker compose start
```

`docker compose down` removes containers and preserves volumes. **Adding `--volumes` deletes this Docker environment's databases, photos and broker data.** It is unnecessary for normal startup.

## Files

- `compose.yaml`: service connections, startup health checks and persistent volumes.
- `backend/Dockerfile`: Maven test/build stage and non-root Java runtime.
- `frontend/Dockerfile`: Vue production build and Nginx runtime.
- `deploy/nginx.conf`: same-origin API routing, cookies and uploads. Upstream DNS resolves container address changes through Docker DNS.
- `deploy/healthcheck-web.sh`: checks the website and all four backend health routes through Nginx, so a working static homepage alone does not mark web healthy.
- `deploy/mysql/00-schema.sql`: fresh-environment schema.
- `deploy/mysql/05-catalogue.sql`: exported public catalogue and per-check images.
- `deploy/mysql/10-create-users.sh`: separate database users.
- `deploy/configure.py`: initial private settings.
- `deploy/package_source.py`: reproducible source ZIP without local settings or build output.

## Verification

Validated on 12 November 2026 using Docker Desktop 4.99.0 on Apple Silicon:

- All seven services built and became healthy; Maven ran 26 tests without failures.
- Ten HTTP acceptance groups passed through the Nginx website, covering customer registration/login, role and ownership restrictions, content publication/version checks, favorites, inspection saves/photos/history, feedback, Kafka report generation, PDF downloads, deletion and account management.
- Browser checks confirmed the customer account menu and the separate administrator Operations interface.
- Containers restarted successfully with persistent MySQL and Kafka volumes. The catalogue retained 9 categories, 45 products and 179 checks. All 179 illustration URLs and all 45 product-photo hashes were checked through the Docker website after restart.
- Labelled test products are archived, feedback is closed and inspection records are deleted; disposable test accounts are disabled. No QA fixtures or customer data are included in the initialization SQL.

To repeat the acceptance checks after startup:

```sh
python3 deploy/test_docker.py
```

This creates labelled temporary records in the Docker environment, exercises the workflows, then cleans them up. Results and a verification PDF are written to `.local/qa-docker/`, outside the source archive. The test cleanup explicitly targets the Compose database, not the native MySQL database. It requires Python 3.9 or later and the configured `.env` administrator to match the account created at first startup.

Configuration follows the official [Compose startup-order documentation](https://docs.docker.com/compose/how-tos/startup-order/) and [Apache Kafka Docker documentation](https://kafka.apache.org/41/getting-started/docker/).
