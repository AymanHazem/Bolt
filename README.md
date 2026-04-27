# ⚡ Bolt: High-Performance URL Shortener

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Redis](https://img.shields.io/badge/Redis-Latest-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Latest-green.svg)](https://www.mongodb.com/)

**Bolt** is a high-performance URL shortening service designed for sub-millisecond redirect resolution. Built with **Spring Boot 4.0.x** and **Java 25**, it leverages **Project Loom (Virtual Threads)**, **Redis caching**, and **MongoDB** to provide a scalable and robust infrastructure for link management.

---

## Key Features

- **Ultra-Fast Redirection:** Achieves sub-millisecond response times using Redis for link resolution.
- **Custom Aliases:** Support for user-defined short links with reserved keyword protection.
- **Dynamic QR Codes:** Automatic generation and caching of high-quality QR codes for every link.
- **Expiration Management:** Set TTL (Time To Live) for links with automatic cleanup.
- **High Concurrency:** Utilizes Java 25 Virtual Threads for efficient resource utilization under heavy load.
- **Docker Ready:** Fully containerized infrastructure with MongoDB, Redis, and Mongo Express.

---

## Tech Stack

- **Runtime:** OpenJDK 25
- **Framework:** Spring Boot 4.0.3
- **Database:** MongoDB (Persistent storage)
- **Cache:** Redis (Redirection layer & QR code cache)
- **QR Engine:** QRGen (based on ZXing)
- **Data Handling:** Jackson (JSON serialization), Lombok
- **Infrastructure:** Docker & Docker Compose

---

## Getting Started

### Prerequisites

- **Java 25+**
- **Maven 3.9+**
- **Docker & Docker Compose**

### 1. Environment Configuration

Copy the `.env.example` file to `.env` and fill in your credentials:

```bash
cp .env.example .env
```

Key variables:

- `MONGO_USERNAME`, `MONGO_PASSWORD`, `MONGO_DATABASE`: MongoDB credentials.
- `APP_BASE_URL`: The public URL of your instance (e.g., `https://bolt.io`).
- `APP_DEFAULT_TTL_HOURS`: Default expiration time for links.

### 2. Launch Infrastructure

Spin up the required databases using Docker Compose:

```bash
docker-compose up -d
```

This will start:

- **MongoDB** on port `27017`
- **Redis** on port `6379`
- **Mongo Express** (UI) on port `8081`

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

---

## API Documentation

### URL Management

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/urls` | Create a shortened URL |
| `GET` | `/api/v1/urls/{alias}` | Get metadata for a specific alias |
| `DELETE` | `/api/v1/urls/{alias}` | Deactivate/Delete a shortened URL |
| `GET` | `/api/v1/urls/{alias}/qr` | Retrieve the QR code image (PNG) |

### Redirection

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/{alias}` | Redirects to the original URL (301/302) |

---

## Performance Optimizations

1. **Virtual Threads:** Enabled by default to handle millions of concurrent connections without thread exhaustion.
2. **Redis Serialization:** Uses optimized string/byte templates for minimal overhead.
3. **Connection Pooling:** Pre-configured for high-throughput database interactions.
4. **Lazy QR Generation:** QR codes are generated on demand and cached for 7 days to save storage.
