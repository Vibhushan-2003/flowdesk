# FlowDesk — Oracle Cloud Always Free ARM64 Provisioning & Deployment Guide

This guide provides operational instructions for deploying FlowDesk to the Oracle Cloud Infrastructure (OCI) Always Free tier using Ampere ARM64 compute, isolated Docker containers, Caddy automated TLS, and automated backups with SRE observability.

---

## 1. Oracle Cloud Always Free Resource Sizing & Tenancy Constraints

### 1.1 Official Tenancy Quotas & Monthly Ceilings
Oracle Cloud Always Free provides compute and storage resources subject to strict monthly allowance metrics:
- **Ampere A1 Compute Grant:** Conservative billing documentation allocates **1,500 A1 OCPU-hours** and **9,000 GB-hours** of memory per tenancy per calendar month.
- **Continuous 24x7 VM Allocation:** 1,500 OCPU-hours allows approximately **2 OCPUs** and **12 GB RAM** running continuously for a full month (30 days &times; 24 hours &times; 2 OCPUs = 1,440 OCPU-hours; 30 days &times; 24 hours &times; 12 GB = 8,640 GB-hours).
- **Block Volume Storage Pool:** **200 GB total eligible storage pool** shared across boot volumes and block volumes across the entire tenancy.
- **Networking:** 1 Free Public IPv4 address per compute instance; 10 TB/month outbound data transfer.

> [!CAUTION]
> **Important Capacity Note on Multi-VM Headroom:**
> Some Oracle marketing materials state "up to 4 OCPUs and 24 GB RAM", corresponding to 3,000 OCPU-hours/month. However, conservative billing enforcement across standard Always Free accounts tracks against **1,500 OCPU-hours / 9,000 GB-hours**. Allocating 2 OCPUs and 12 GB RAM for FlowDesk utilizes ~96–99% of that monthly allowance. **Do not assume another 2 OCPU / 12 GB instance can be run concurrently without incurring charges or encountering capacity rejection.**
>
> During VM provisioning in the OCI Console, the operator **MUST verify** that:
> 1. The instance shape displays the **"Always Free-eligible"** tag.
> 2. The **"Cost Estimation"** panel in the creation drawer displays strictly **$0.00 / month**.
> 3. Never select "High Performance" volume VPUs (use "Balanced" 30 VPU/GB included in Always Free).

### 1.2 FlowDesk Production VM Specification

| Component | Target Specification | Tenancy Consumption & Safety Rule |
| :--- | :--- | :--- |
| **Compute Shape** | `VM.Standard.A1.Flex` (Ampere Altra ARM64) | Must display **Always Free-eligible** |
| **OCPUs** | **2 OCPUs** | Consumes ~1,440 of 1,500 monthly OCPU-hours |
| **RAM** | **12 GB RAM** | Consumes ~8,640 of 9,000 monthly GB-hours |
| **Boot Volume** | **60 GB** (Balanced Performance, 30 VPU/GB) | Leaves 140 GB in 200 GB eligible block volume pool |
| **Operating System** | **Ubuntu 24.04 LTS Minimal ARM64** (`aarch64`) | Official Canonical minimal cloud image |
| **Public IPv4** | 1 Ephemeral or Reserved Public IPv4 | Included in Always Free allocation |

### 1.3 Memory Allocation Budget (12 GB RAM)
- **PostgreSQL 16:** ~512 MB – 1 GB (shared buffers, work mem)
- **Spring Boot Backend (JVM):** 1.5 GB heap (`-Xms512m -Xmx2048m`), ~500 MB native/metaspace
- **ClamAV Daemon:** ~1.2 GB (virus signature database resident in RAM)
- **React / Nginx Static Frontend:** ~50 MB
- **Caddy Reverse Proxy:** ~80 MB
- **Prometheus + Node Exporter:** ~300 MB
- **Grafana + Alertmanager:** ~200 MB
- **Host OS & Linux Filesystem Cache:** ~5+ GB (available for OS disk caching and page buffers)

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
5. Click on `flowdesk-prod-vcn` &rarr; click on **Security Lists** &rarr; select **Default Security List for flowdesk-prod-vcn**.
6. **Configure Ingress Rules:**

   | Source CIDR | IP Protocol | Destination Port Range | Description & Hardening |
   | :--- | :--- | :--- | :--- |
   | `<OPERATOR_IP>/32` *(or `0.0.0.0/0`)* | TCP | **22** | SSH Administration (**strongly recommended to restrict to operator's static IP/CIDR**) |
   | `0.0.0.0/0` | TCP | **80** | HTTP Ingress (Automated ACME HTTP-01 challenge & HTTPS redirection) |
   | `0.0.0.0/0` | TCP | **443** | HTTPS Edge Ingress (Caddy TLS termination) |

   > [!IMPORTANT]
   > Do **NOT** add ingress rules for ports `5432` (PostgreSQL), `8080` (Backend), `3310` (ClamAV), `9090` (Prometheus), `3000` (Grafana), `9093` (Alertmanager), or `9100` (Node Exporter). These services are private to Docker bridge networks.

7. **Verify Egress Rule:**
   - Default Egress Rule must allow `0.0.0.0/0` for all protocols (required for OS security patches, ClamAV freshclam virus definitions, and outbound SMTP relays).

---

### Step 2: Launch Compute Instance
1. Navigate to **Compute** &rarr; **Instances** &rarr; click **Create Instance**.
2. **Name:** `flowdesk-prod-vm1`
3. **Compartment:** Select production compartment.
4. **Placement:** Select an Availability Domain (e.g. `AD-1`, `AD-2`, or `AD-3` where A1 capacity is available).
5. **Image and Shape:**
   - Click **Change Image** &rarr; select **Canonical Ubuntu** &rarr; choose **Ubuntu 24.04 Minimal aarch64**.
   - Click **Change Shape** &rarr; select **Ampere (ARM)** &rarr; choose `VM.Standard.A1.Flex`.
   - Set **OCPUs:** `2`
   - Set **Memory (GB):** `12`
   - **Verify:** Confirm the **"Always Free-eligible"** badge is present.
6. **Networking:**
   - **Primary Network:** Select `flowdesk-prod-vcn`.
   - **Subnet:** Select `Public Subnet-flowdesk-prod-vcn`.
   - **Assign Public IPv4 Address:** Select **Automatically assign public IPv4 address** (or attach a Reserved Public IP).
7. **SSH Keys:**
   - Upload your secure public key (`~/.ssh/id_ed25519.pub`).
8. **Boot Volume:**
   - Check **Specify a custom boot volume size**.
   - Set **Size (GB):** `60`.
   - Volume Performance: **Balanced (30 VPU/GB)** (Default, eligible for Always Free).
9. **Cost Estimation Check:**
   - Check the bottom estimation bar: Ensure estimated cost reads **$0.00 / month**.
10. Click **Create** and record the assigned **Public IPv4 Address** once provisioning transitions to *Running*.

---

### Step 3: Configure Host OS Firewall (SSH Preservation & Non-Brittle Rules)
> [!CAUTION]
> Canonical Ubuntu images on Oracle Cloud ship with a default `/etc/iptables/rules.v4` firewall configuration that rejects incoming TCP traffic on ports 80 and 443. Never use brittle fixed rule index insertions (e.g. `iptables -I INPUT 6 ...`), which can fail or disrupt SSH access across different image releases.

Connect via SSH:
```bash
ssh -i ~/.ssh/id_ed25519 ubuntu@<YOUR_OCI_PUBLIC_IP>
```

#### Option A: UFW (Recommended for Ubuntu 24.04)
UFW automatically handles safe rule ordering while explicitly preserving active SSH sessions:
```bash
# 1. Set safe defaults
sudo ufw default deny incoming
sudo ufw default allow outgoing

# 2. Explicitly allow SSH, HTTP, and HTTPS
sudo ufw allow 22/tcp comment 'SSH Administration'
sudo ufw allow 80/tcp comment 'HTTP Ingress & ACME'
sudo ufw allow 443/tcp comment 'HTTPS Edge Ingress'

# 3. Enable UFW
sudo ufw --force enable
sudo ufw status verbose
```

#### Option B: Robust iptables (Without fragile rule numbers)
If using raw netfilter/iptables, insert rules at the top of the `INPUT` chain in a controlled sequence that guarantees SSH is never blocked:
```bash
# 1. Preserve established connections and loopback traffic
sudo iptables -I INPUT 1 -m state --state RELATED,ESTABLISHED -j ACCEPT
sudo iptables -I INPUT 2 -i lo -j ACCEPT

# 2. Prioritize SSH to prevent lockout
sudo iptables -I INPUT 3 -p tcp --dport 22 -j ACCEPT

# 3. Allow HTTP (80) and HTTPS (443) for Caddy edge ingress
sudo iptables -I INPUT 4 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 5 -p tcp --dport 443 -j ACCEPT

# 4. Persist rules across host reboots
sudo apt update && sudo apt install -y iptables-persistent netfilter-persistent
sudo netfilter-persistent save
```

---

### Step 4: Install Docker Engine & Compose Plugin on Ubuntu ARM64

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

# 2. Create backup and observability directories with restrictive permissions
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
   - **Name:** `tickets` (e.g. `tickets.yourdomain.com`)
   - **Value:** `<YOUR_OCI_PUBLIC_IP>`
   - **TTL:** `300` (5 minutes)
   - **Proxy Status:** DNS Only (disable Cloudflare orange-cloud proxy during initial ACME certificate issuance).

2. Edit `/opt/flowdesk/.env.prod`:
   ```bash
   nano /opt/flowdesk/.env.prod
   ```
   Set your public domain, ACME email, generated database password, and JWT secret:
   ```properties
   # Production Domain & Automated ACME TLS
   CADDY_DOMAIN=tickets.yourdomain.com
   CADDY_TLS=admin@yourdomain.com
   FRONTEND_ORIGIN=https://tickets.yourdomain.com
   FLOWDESK_FRONTEND_ORIGIN=https://tickets.yourdomain.com

   # Strong Database Password (generated e.g. via: openssl rand -base64 32)
   DB_PASSWORD=YourStrongDatabasePassword123456789!

   # Strong 256-bit JWT Secret (generated e.g. via: openssl rand -base64 32)
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

   > [!IMPORTANT]
   > Do **NOT** store `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD` in `.env.prod`. First administrator initialization must use the secret-safe transient environment procedure in Step 7.

---

### Step 7: Launch Stack & Provision First Administrator (Secret-Safe)

1. Build and start the production stack:
   ```bash
   cd /opt/flowdesk
   docker compose -f docker-compose.prod.yml up -d --build
   ```

2. Wait for backend and database health:
   ```bash
   docker compose -f docker-compose.prod.yml ps
   # Backend and Postgres will report (healthy)
   ```

3. **Provision the First Administrator (Secret-Safe Non-Web CLI):**
   To prevent credentials from leaking into bash history, process lists (`ps aux`), or `.env.prod`:

   ```bash
   # Method A: Transient restricted environment file (Recommended)
   install -m 0600 /dev/null /tmp/admin_bootstrap.env
   cat << 'EOF' > /tmp/admin_bootstrap.env
   FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true
   FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com
   FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=YourStrongInitialAdminPassword123!
   EOF

   # Execute one-time bootstrap
   docker compose -f docker-compose.prod.yml run --rm --env-file /tmp/admin_bootstrap.env backend --flowdesk.bootstrap.admin

   # Securely destroy the transient file immediately
   shred -u /tmp/admin_bootstrap.env 2>/dev/null || rm -f /tmp/admin_bootstrap.env
   ```

   *(Alternative Method B via interactive hidden input):*
   ```bash
   read -s -p "Enter initial admin password: " ADMIN_PASS
   echo
   FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASS" docker compose -f docker-compose.prod.yml run --rm \
     -e FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true \
     -e FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com \
     -e FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD \
     backend --flowdesk.bootstrap.admin
   unset ADMIN_PASS
   ```

   *Expected Output:*
   ```text
   Starting FlowDesk in non-web bootstrap mode (WebApplicationType.NONE).
   Acquiring lock on ADMIN role row for initial bootstrap check...
   Successfully provisioned initial administrator with email: admin@yourdomain.com
   Terminating application process after successful bootstrap (exit code 0).
   ```
   *Note: This command runs in non-web mode, applies changes, and exits 0 immediately. Future runs against a non-empty database are rejected automatically.*

---

### Step 8: Install Systemd Backup Automation & Metrics Collection

1. Configure backup credentials:
   ```bash
   sudo cp /opt/flowdesk/scripts/sre/flowdesk-backup.env.example /etc/flowdesk/backup.env
   sudo chmod 600 /etc/flowdesk/backup.env
   ```
   Edit `/etc/flowdesk/backup.env` to configure off-host replication (`DEST_TYPE=oci_cli`, `OCI_BUCKET=...`, `BACKUP_ENCRYPTION_KEY=...`).

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
   Confirm that Prometheus metrics were generated in the textfile directory:
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

### Step 10: Run the Production Deployment Smoke Test Suite
Validate the complete production deployment across all 13 criteria:

```bash
cd /opt/flowdesk

# Option A: Interactive prompt for admin password (prevents CLI password exposure)
./scripts/sre/deployment_smoke_test.sh \
  --domain tickets.yourdomain.com \
  --user admin@yourdomain.com \
  --strict

# Option B: Using a protected env file
install -m 0600 /dev/null /tmp/smoke.env
cat << 'EOF' > /tmp/smoke.env
SMOKE_TEST_USER=admin@yourdomain.com
SMOKE_TEST_PASSWORD=YourStrongInitialAdminPassword123!
EOF

./scripts/sre/deployment_smoke_test.sh \
  --domain tickets.yourdomain.com \
  --env-file /tmp/smoke.env \
  --strict

shred -u /tmp/smoke.env 2>/dev/null || rm -f /tmp/smoke.env
```

*Expected Result:*
```text
======================================================================
Smoke Test Summary
======================================================================
  Passed:             13
  Failed:             0
  Skipped:            0
  Strict Violations:  0

>>> SMOKE TEST RESULT: ALL CHECKS PASSED - STACK IS READY <<<
```
