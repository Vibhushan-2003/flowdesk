# FlowDesk — Oracle Cloud Always Free ARM64 Provisioning & Deployment Guide

This guide provides end-to-end operational instructions for deploying FlowDesk to Oracle Cloud Infrastructure (OCI) Always Free tier using Ampere ARM64 compute, isolated Docker containers, Caddy automated TLS, and automated backups with SRE observability.

---

## 1. Oracle Cloud Always Free Resource Sizing

### 1.1 Tenancy Limits & Allocation
Oracle Cloud Always Free provides the following tenancy-wide limits for Ampere A1 Compute:
- **Compute:** 4 OCPUs and 24 GB RAM (can be allocated to 1 to 4 VMs).
- **Block Volume Storage:** 200 GB pooled across boot volumes and block volumes.
- **Networking:** 1 Free Public IPv4 address per compute instance; 10 TB/month outbound bandwidth.

### 1.2 FlowDesk Production VM Specification
To leave generous capacity for staging or disaster recovery while maximizing single-node performance:

| Component | Specification | Tenancy Headroom Remaining |
| :--- | :--- | :--- |
| **Compute Shape** | `VM.Standard.A1.Flex` (Ampere Altra ARM64) | — |
| **OCPUs** | **2 OCPUs** | 2 OCPUs free |
| **RAM** | **12 GB RAM** | 12 GB free |
| **Boot Volume** | **60 GB** (Balanced Performance, 30 VPU/GB) | 140 GB free |
| **Operating System** | **Ubuntu 24.04 LTS Minimal ARM64** (`aarch64`) | — |
| **Public IPv4** | 1 Ephemeral or Reserved Public IPv4 | Free tier |

### 1.3 Memory Allocation Budget (12 GB RAM)
- **PostgreSQL 16:** ~512 MB – 1 GB (shared buffers, work mem)
- **Spring Boot Backend (JVM):** 1.5 GB heap (`-Xms512m -Xmx2048m`), ~500 MB native/metaspace
- **ClamAV Daemon:** ~1.2 GB (virus definition database in memory)
- **React / Nginx Frontend:** ~50 MB
- **Caddy Reverse Proxy:** ~80 MB
- **Prometheus + Node Exporter:** ~300 MB
- **Grafana + Alertmanager:** ~200 MB
- **Host OS & Buffer Cache:** ~5 GB (available for Linux filesystem cache and dirty page buffers)

---

## 2. Step-by-Step OCI Console Provisioning

### Step 1: Virtual Cloud Network (VCN) & Security List
1. Open the [Oracle Cloud Console](https://cloud.oracle.com).
2. Navigate to **Networking** &rarr; **Virtual Cloud Networks**.
3. Click **Start VCN Wizard** &rarr; select **Create VCN with Internet Connectivity** &rarr; **Start Wizard**.
   - **VCN Name:** `flowdesk-prod-vcn`
   - **Compartment:** Root or designated production compartment.
   - **VCN IPv4 CIDR:** `10.0.0.0/16`
   - **Public Subnet CIDR:** `10.0.0.0/24`
   - **Private Subnet CIDR:** `10.0.1.0/24`
4. Click **Next** &rarr; **Create**.
5. Once created, click on `flowdesk-prod-vcn` &rarr; click on **Security Lists** &rarr; select **Default Security List for flowdesk-prod-vcn**.
6. **Ingress Rules Configuration:**
   Configure exactly the following ingress rules:

   | Source CIDR | IP Protocol | Source Port Range | Destination Port Range | Description |
   | :--- | :--- | :--- | :--- | :--- |
   | `0.0.0.0/0` | TCP | All | **22** | SSH Administration (optionally restrict to operator IP) |
   | `0.0.0.0/0` | TCP | All | **80** | HTTP Ingress (ACME HTTP-01 challenge & HTTPS redirection) |
   | `0.0.0.0/0` | TCP | All | **443** | HTTPS Public Edge Ingress (Caddy TLS) |

   > [!IMPORTANT]
   > Do **NOT** add ingress rules for ports `5432` (PostgreSQL), `8080` (Backend), `3310` (ClamAV), `9090` (Prometheus), `3000` (Grafana), `9093` (Alertmanager), or `9100` (Node Exporter). These services are private to Docker bridge networks.

7. **Egress Rules:**
   - Verify Default Egress Rule exists: Destination `0.0.0.0/0`, All Protocols (allows outbound OS updates, ClamAV virus signatures, and SMTP relays).

---

### Step 2: Launch Compute Instance
1. Navigate to **Compute** &rarr; **Instances** &rarr; click **Create Instance**.
2. **Name:** `flowdesk-prod-vm1`
3. **Compartment:** Select your compartment.
4. **Placement:** Select an Availability Domain (e.g. `AD-1`). If capacity is temporarily constrained, try `AD-2` or `AD-3`.
5. **Image and Shape:**
   - Click **Change Image** &rarr; select **Canonical Ubuntu** &rarr; choose **Ubuntu 24.04 Minimal aarch64**.
   - Click **Change Shape** &rarr; select **Ampere (ARM)** &rarr; choose `VM.Standard.A1.Flex`.
   - Set **OCPUs:** `2`
   - Set **Memory (GB):** `12`
6. **Networking:**
   - **Primary Network:** Select `flowdesk-prod-vcn`.
   - **Subnet:** Select `Public Subnet-flowdesk-prod-vcn`.
   - **Assign Public IPv4 Address:** Select **Automatically assign public IPv4 address** (or attach a Reserved Public IP).
7. **SSH Keys:**
   - Select **Upload public key files (.pub)** and upload your secure SSH public key (e.g. `~/.ssh/id_ed25519.pub`).
8. **Boot Volume:**
   - Check **Specify a custom boot volume size**.
   - Set **Size (GB):** `60`.
   - Volume Performance: **Balanced** (Default).
9. Click **Create**. Instance provisioning typically completes in 60–90 seconds.
10. Note the assigned **Public IPv4 Address** (e.g. `129.150.x.x`).

---

### Step 3: Configure Host OS Firewall (iptables / netfilter)
> [!CAUTION]
> Canonical Ubuntu images on Oracle Cloud include a default `/etc/iptables/rules.v4` firewall configuration that rejects incoming TCP traffic on ports 80 and 443, even when the OCI VCN Security List permits it! You must open these ports on the host OS.

Connect to your instance via SSH:
```bash
ssh -i ~/.ssh/id_ed25519 ubuntu@<YOUR_OCI_PUBLIC_IP>
```

Open ports 80 and 443 in `iptables` and make the rules persistent across reboots:
```bash
# Insert rules before the final REJECT rule in the INPUT chain
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT

# Persist iptables rules across reboots
sudo apt update && sudo apt install -y iptables-persistent netfilter-persistent
sudo netfilter-persistent save
```

*(Alternative using UFW if preferred):*
```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
```

---

### Step 4: Install Docker Engine & Compose Plugin on Ubuntu ARM64
Install the official Docker CE repository and runtime:

```bash
# 1. Install prerequisites
sudo apt update && sudo apt install -y ca-certificates curl gnupg lsb-release

# 2. Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. Set up Docker repository for Ubuntu ARM64 (noble)
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. Install Docker Engine, CLI, and Compose plugin
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. Enable non-root docker access for ubuntu user
sudo usermod -aG docker ubuntu
newgrp docker
docker version
```

---

### Step 5: Directory Structure, Permissions & Code Deployment

```bash
# 1. Clone FlowDesk repository into /opt/flowdesk
sudo mkdir -p /opt/flowdesk
sudo chown ubuntu:ubuntu /opt/flowdesk
git clone https://github.com/Vibhushan-2003/flowdesk.git /opt/flowdesk
cd /opt/flowdesk

# 2. Create backup and observability directories
sudo mkdir -p /var/backups/flowdesk
sudo chmod 700 /var/backups/flowdesk
sudo mkdir -p /var/lib/node_exporter/textfile_collector
sudo chmod 755 /var/lib/node_exporter/textfile_collector
sudo mkdir -p /etc/flowdesk
sudo chmod 700 /etc/flowdesk

# 3. Configure production environment
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

---

### Step 6: Public DNS & TLS Domain Configuration
1. In your DNS provider (Cloudflare, Namecheap, Route 53, etc.), create an `A` record:
   - **Type:** `A`
   - **Name:** `tickets` (or `@` for apex domain)
   - **Value:** `<YOUR_OCI_PUBLIC_IP>`
   - **TTL:** `300` (5 minutes)
   - **Proxy Status:** DNS Only (disable Cloudflare orange cloud proxy during initial ACME certificate issuance).

2. Edit `/opt/flowdesk/.env.prod`:
   ```bash
   nano /opt/flowdesk/.env.prod
   ```
   Set your public domain and ACME email:
   ```properties
   # Production Domain & Automated ACME TLS
   CADDY_DOMAIN=tickets.yourdomain.com
   CADDY_TLS=admin@yourdomain.com
   FRONTEND_ORIGIN=https://tickets.yourdomain.com
   FLOWDESK_FRONTEND_ORIGIN=https://tickets.yourdomain.com

   # Generate strong random database password
   # (e.g. openssl rand -base64 32)
   DB_PASSWORD=YourStrongDatabasePassword123456789!

   # Generate strong JWT secret
   # (e.g. openssl rand -base64 32)
   JWT_SECRET=YourStrongBase64EncodedJwtSecretMin32Bytes==

   # Outbound SMTP Configuration (Brevo, Resend, or SendGrid)
   FLOWDESK_MAIL_ENABLED=true
   SMTP_HOST=smtp-relay.brevo.com
   SMTP_PORT=587
   SMTP_USERNAME=your-smtp-account@example.com
   SMTP_PASSWORD=your-smtp-api-key
   SMTP_AUTH=true
   SMTP_STARTTLS=true
   FLOWDESK_MAIL_FROM_ADDRESS=support@tickets.yourdomain.com
   FLOWDESK_MAIL_FROM_NAME=FlowDesk Support

   # SRE Monitoring Credentials (if monitoring profile is activated)
   GRAFANA_ADMIN_USER=flowdesk_admin
   GRAFANA_ADMIN_PASSWORD=YourStrongGrafanaPasswordMin12Chars!
   NODE_EXPORTER_TEXTFILE_DIR=/var/lib/node_exporter/textfile_collector
   ```

---

### Step 7: Launch Core Stack & Provision First Administrator

1. Build and start the production stack:
   ```bash
   cd /opt/flowdesk
   docker compose -f docker-compose.prod.yml up -d --build
   ```

2. Wait for backend and database health:
   ```bash
   docker compose -f docker-compose.prod.yml ps
   # Backend and Postgres will transition to healthy
   ```

3. **Provision the First Administrator (Non-Web CLI Bootstrap):**
   Execute the idempotent bootstrap CLI command:
   ```bash
   docker compose -f docker-compose.prod.yml run --rm \
     -e FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true \
     -e FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com \
     -e FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD='YourStrongInitialAdminPassword123!' \
     backend --flowdesk.bootstrap.admin
   ```
   *Expected output:*
   ```text
   FlowDesk initial administrator [admin@yourdomain.com] provisioned successfully.
   ```
   *Note: This command runs in non-web mode, provisions the admin user in PostgreSQL, and immediately terminates. Future runs on a non-empty database are rejected automatically.*

---

### Step 8: Install Systemd Backup Automation & Metrics Collection

1. Configure backup credentials:
   ```bash
   sudo cp /opt/flowdesk/scripts/sre/flowdesk-backup.env.example /etc/flowdesk/backup.env
   sudo chmod 600 /etc/flowdesk/backup.env
   ```
   Edit `/etc/flowdesk/backup.env` to configure off-host replication (OCI Object Storage or S3) and passphrases.

2. Install systemd service and timer units:
   ```bash
   sudo cp /opt/flowdesk/scripts/sre/systemd/flowdesk-backup.service /etc/systemd/system/
   sudo cp /opt/flowdesk/scripts/sre/systemd/flowdesk-backup.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now flowdesk-backup.timer
   ```

3. Verify timer scheduling:
   ```bash
   systemctl list-timers flowdesk-backup.timer
   ```

4. Trigger an immediate manual test backup:
   ```bash
   sudo /opt/flowdesk/scripts/sre/flowdesk-backup.sh
   ```
   Confirm that Prometheus metrics were generated:
   ```bash
   cat /var/lib/node_exporter/textfile_collector/flowdesk_backup.prom
   ```

---

### Step 9: Activate SRE Monitoring Stack (Optional)
To enable Prometheus, Grafana, Alertmanager, and Node Exporter:
```bash
docker compose -f docker-compose.prod.yml --profile monitoring up -d
```

To securely inspect Grafana from your local workstation without opening public firewall ports:
```bash
# On your local laptop:
ssh -L 3000:172.28.0.31:3000 ubuntu@<YOUR_OCI_PUBLIC_IP>
# Open http://localhost:3000 in your browser and log in with GRAFANA_ADMIN_PASSWORD
```

---

### Step 10: Run the Deployment Smoke Test Suite
Validate the complete production deployment across all 13 criteria:

```bash
cd /opt/flowdesk
./scripts/sre/deployment_smoke_test.sh \
  --domain tickets.yourdomain.com \
  --user admin@yourdomain.com \
  --password 'YourStrongInitialAdminPassword123!'
```

*Expected Result:*
```text
======================================================================
Smoke Test Summary
======================================================================
  Passed:  13
  Failed:  0
  Skipped: 0

>>> SMOKE TEST RESULT: ALL CHECKS PASSED - STACK IS READY <<<
```

