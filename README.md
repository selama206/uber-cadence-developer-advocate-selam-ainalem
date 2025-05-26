# Cadence Eating Demo

This project demonstrates a food ordering workflow using Uber's Cadence workflow engine. It processes orders from a CSV file and manages the order lifecycle through Cadence workflows.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Setup](#setup)
  - [Cadence Server](#1-cadence-server)
  - [Platform-Specific Considerations](#2-platform-specific-considerations)
- [Running the Demo](#running-the-demo)
  - [Running Workflows (Recommended Method)](#running-workflows-recommended-method)
    - [Manual Workflow Commands](#manual-workflow-commands)
  - [Monitoring Workflows](#monitoring-workflows)
- [Workflow States](#workflow-states)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [FAQ / Debugging Guide](#faq--debugging-guide)

## Prerequisites

- Docker and Docker Compose
- Java 8 or later
- Maven
- [Cadence CLI](https://cadenceworkflow.io/docs/cli)
- A running Cadence server (see Setup section)

## Setup

### 1. Cadence Server

This project expects to connect to a running Cadence server. You have two options:

#### Option A: Use Existing Cadence Server (Recommended)
If you have Cadence running from the main Cadence repository (typically in `/Users/<username>/Coding/Uber/cadence/docker`):

1. Make sure the Cadence server is running (containers should be named `docker-cadence-1`, `docker-cassandra-1`, etc.)
2. Note the Docker network name (typically `docker_default`)
3. The docker-compose.yml in this project is configured to connect to this network
4. Verify the Cadence port number:
   ```bash
   docker ps | grep cadence
   ```
   Look for the port mapping like `7933->7933`. If it's different, update:
   - docker-compose.yml: CADENCE_CLI_ADDRESS=docker-cadence-1:PORT
   - process_orders.sh: --address docker-cadence-1:PORT
5. Register your domain if not already done:
   ```bash
   cadence --address localhost:7933 --domain samples-domain domain register
   ```

#### Option B: Run Standalone
If you want to run everything independently:

1. Remove the `networks` section from docker-compose.yml
2. Uncomment the Cassandra and Cadence services in docker-compose.yml
3. Update the CADENCE_CLI_ADDRESS environment variable to use `cadence:7933`

### 2. Platform-Specific Considerations

#### macOS Users
- **Intel-based Macs**: Default configuration should work as is
- **Apple Silicon (M1/M2/M3)**:
  - Some Docker images may show platform mismatch warnings (amd64 vs arm64)
  - These warnings can be safely ignored as Docker will use emulation
  - If you experience performance issues, look for arm64-compatible images

#### Network Mode
- The `network_mode: host` setting doesn't work the same on all platforms
- macOS users should avoid `network_mode: host` as it doesn't work as expected
- Instead, use Docker's bridge networking (default) or join an existing network as configured

## Running the Demo

### Running Workflows (Recommended Method)

1. Start the worker (only needed once, or after worker code changes):
```bash
docker-compose build --no-cache worker && docker-compose up -d worker
```

2. Process orders from CSV:
```bash
docker-compose build --no-cache processor && docker-compose run processor sample_orders.csv
```

This method:
- Automatically generates valid UUIDs for orders, users, and restaurants
- Handles workflow timing and retries automatically
- Ensures proper network connectivity
- Manages signal timing correctly
- Provides clear logging and error messages

The processor will:
- Read orders from sample_orders.csv
- Start workflows for each order
- Send accept/reject signals based on the CSV status
- Show progress in the logs
- Handle all timing and coordination automatically

Example successful output:
```
Starting workflow for order: cb8be1a8...
Workflow ID: 11f67c55-27b5-43e8-b18b-bc8fbeaffe7a
Signal sent successfully
All orders processed!
```

### Manual Workflow Commands

<details>
<summary>Manual Workflow Commands (Advanced Users Only - Click to Expand)</summary>

⚠️ **WARNING**: Manual workflow creation is NOT recommended for normal use. Use the Docker-based processor instead.
Only use these commands if you need to test specific scenarios or debug workflow behavior.

#### Required Variables
```bash
# Unique identifiers (must be UUID v4 format)
WORKFLOW_ID="11f67c55-27b5-43e8-b18b-bc8fbeaffe7a"  # Unique identifier for this workflow
USER_ID="a83b85d8-e294-e7b0-079e-0842c9f74dad"      # Unique identifier for the user
ORDER_ID="cb8be1a8-ec1b-0064-78f7-367d522355ae"     # Unique identifier for the order
RESTAURANT_ID="274acd37-77ea-65f7-e2e7-f81ace30937b" # Unique identifier for the restaurant

# Order content (must be valid JSON array)
ITEMS='["sandwich","grapes","chips"]'

# Restaurant decision (must be boolean)
DECISION="true"  # true for accept, false for reject

# Combined order JSON (must match schema)
ORDER_JSON="{\"id\":\"$ORDER_ID\",\"content\":$ITEMS}"
```

#### 1. Start Workflow
```bash
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow start \
    --workflow_id $WORKFLOW_ID \
    --tasklist HandleEatsOrderTaskList \
    --workflow_type HandleEatsOrderWorkflow::handleOrder \
    --execution_timeout 600 \
    --input "[$USER_ID, $ORDER_JSON, $RESTAURANT_ID]"
```

#### 2. Send Restaurant Decision
```bash
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow signal \
    --workflow_id $WORKFLOW_ID \
    --name HandleEatsOrderWorkflow::signalRestaurantDecision \
    --input $DECISION
```

#### Example Usage
```bash
# Set up variables
export WORKFLOW_ID=$(uuidgen)
export USER_ID=$(uuidgen)
export ORDER_ID=$(uuidgen)
export RESTAURANT_ID=$(uuidgen)
export ITEMS='["burger","fries","soda"]'
export DECISION="true"
export ORDER_JSON="{\"id\":\"$ORDER_ID\",\"content\":$ITEMS}"

# Start workflow
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow start \
    --workflow_id $WORKFLOW_ID \
    --tasklist HandleEatsOrderTaskList \
    --workflow_type HandleEatsOrderWorkflow::handleOrder \
    --execution_timeout 600 \
    --input "[$USER_ID, $ORDER_JSON, $RESTAURANT_ID]"

# Wait for workflow to initialize (at least 5 seconds)
sleep 5

# Send decision
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow signal \
    --workflow_id $WORKFLOW_ID \
    --name HandleEatsOrderWorkflow::signalRestaurantDecision \
    --input $DECISION
```

#### Important Notes
1. **Timing**: There must be a delay between starting the workflow and sending the signal
2. **UUID Format**: All IDs must be valid UUID v4 format
3. **JSON Format**: Order content must be a valid JSON array
4. **Network**: Commands must run on the same Docker network as Cadence
5. **Variables**: All environment variables must be properly set and escaped
6. **Monitoring**: Use Cadence Web UI to monitor workflow progress

⚠️ **Remember**: The automated processor handles all of these details automatically. Only use manual commands if absolutely necessary for testing or debugging.

</details>

### Monitoring Workflows

Access the Cadence Web UI:
- If using existing Cadence server: http://localhost:8088
- If running standalone: http://localhost:8088

You can monitor:
- Workflow execution status
- Activity progress
- Signal delivery
- Timing and retry information

Note: We strongly recommend using the Docker-based processor approach rather than manually starting workflows. The processor ensures proper timing, retry logic, and signal coordination that are difficult to achieve with manual workflow creation.

## Workflow States

The order goes through these states:
1. Order Received - Initial state
2. Waiting for Restaurant - Awaiting accept/reject signal
3. Preparing Order - If accepted, 3-second preparation
4. Delivering Order - Child workflow, 4-second delivery
5. Delivered - Final success state
6. Rejected - Final rejected state

## Monitoring

Access the Cadence Web UI:
- If using existing Cadence server: http://localhost:8088
- If running standalone: http://localhost:8088

## Troubleshooting

### Common Issues

1. **Connection Refused**
   - Check if Cadence server is running
   - Verify network configuration in docker-compose.yml
   - Ensure CADENCE_CLI_ADDRESS points to the correct host
   - Check if you're using the right network (`docker_default` vs standalone)

2. **Platform Mismatch Warnings**
   ```
   The requested image's platform (linux/amd64) does not match the detected host platform (linux/arm64/v8)
   ```
   - This is normal on Apple Silicon Macs
   - Docker will handle emulation automatically

3. **Network Issues**
   - If using existing Cadence server, ensure `docker_default` network exists
   - If running standalone, remove external network configuration
   - For manual CLI commands, make sure to use the right network and address

### Checking Configuration

To verify your setup:
```bash
# Check running containers
docker ps

# Verify network
docker network ls | grep docker_default

# Check logs
docker-compose logs worker
docker-compose logs processor
```

## Project Structure

```
.
├── Dockerfile           # Worker container configuration
├── Dockerfile.processor # Order processor configuration
├── docker-compose.yml  # Service orchestration
├── process_orders.sh   # CSV processing script
├── sample_orders.csv   # Example orders
└── src/               # Java source code
```

## Architecture

- `HandleEatsOrderWorkflow`: Main workflow for order processing
- `DeliverOrderWorkflow`: Child workflow for delivery handling
- CSV Processor: Reads orders and triggers workflows
- Worker: Executes workflow and activity code

## FAQ / Debugging Guide

### Cadence Connection Issues

#### Expected Cadence Configuration
By default, the application expects to connect to a Cadence server with the following configuration:
- Container name: By default, it will be named based on your Cadence directory name, typically `docker-cadence-1`
- Port: `7933`
- Network: Must be on the `docker_default` network
- Container labels: Will have auto-generated Docker Compose labels including:
  ```
  com.docker.compose.project=docker
  com.docker.compose.service=cadence
  ```

#### Common Issues and Solutions

1. **"My worker isn't connecting to Cadence"**
   
   Symptoms:
   - Worker logs show connection timeouts
   - No workflows are being picked up
   - Worker exits immediately
   
   Solutions:
   - Check worker logs:
     ```bash
     # Should show correct Cadence address
     docker-compose logs worker | grep "Using Cadence address"
     ```
   - If showing `127.0.0.1:7933`, rebuild the worker:
     ```bash
     docker-compose build --no-cache worker && docker-compose up -d worker
     ```
   - Verify network connectivity:
     ```bash
     # Check if containers are on the same network
     docker network inspect docker_default | grep -A 5 "\"Containers\""
     ```

2. **"My workflows aren't being processed"**

   Symptoms:
   - Workflows appear in Cadence UI but stay in "Running" state
   - No activity logs in worker
   - Processor shows success but nothing happens
   
   Solutions:
   - Verify task list registration:
     ```bash
     # Should show both task lists registered
     docker-compose logs worker | grep "Created.*worker.*task list"
     ```
   - Check environment configuration:
     ```bash
     # Should show correct CADENCE_CLI_ADDRESS
     docker-compose config | grep CADENCE
     ```
   - Test end-to-end workflow:
     ```bash
     docker-compose run processor sample_orders.csv
     ```

3. **Network Configuration**
   - Problem: Services can't communicate
   - Solution: Ensure proper network setup in docker-compose.yml:
     ```yaml
     services:
       worker:
         networks:
           - docker_default
     networks:
       docker_default:
         external: true
     ```

4. **Environment Variables**
   - Problem: Wrong Cadence host configuration
   - Solution: Set correct host:
     ```bash
     # Option 1: Environment variable
     export CADENCE_HOST=docker-cadence-1
     
     # Option 2: Docker Compose default
     CADENCE_CLI_ADDRESS: ${CADENCE_HOST:-docker-cadence-1}:7933
     ```

5. **Worker Code/Configuration Mismatch**
   - Problem: Old worker code not using environment variables
   - Solution: Rebuild with latest code:
     ```bash
     docker-compose build --no-cache worker
     docker-compose up -d worker
     ```

6. **Task List Mismatch**
   - Problem: Workflows started but not picked up
   - Solution: Verify task list names:
     ```bash
     # Check worker registration
     docker-compose logs worker | grep "task list"
     
     # Check workflow start
     docker-compose logs processor | grep "workflow command"
     ```

### Quick Debug Checklist
Most issues can be resolved by checking:
- ✓ Network configuration (services on same network)
- ✓ Environment variables (correct Cadence host)
- ✓ Worker code/configuration (recent build)
- ✓ Task list names (matching between components)
- ✓ Logs (for specific error messages)

For more detailed Cadence debugging, refer to the [Cadence Documentation](https://cadenceworkflow.io/docs/operation-guide/troubleshooting/).

### Environment Variables Explained

#### Cadence Connection Variables
- `CADENCE_CLI_ADDRESS`: The full address (host:port) for connecting to Cadence
  - Format: `host:port` (e.g., `docker-cadence-1:7933`)
  - Used by both the worker and processor to connect to Cadence
  - Historical name from Cadence CLI tool, but actually used for all Cadence connections

- `CADENCE_HOST`: A convenience variable for setting just the host portion
  - Only the hostname (e.g., `docker-cadence-1`)
  - Used in docker-compose.yml to construct `CADENCE_CLI_ADDRESS`
  - Example usage:
    ```yaml
    environment:
      - CADENCE_CLI_ADDRESS=${CADENCE_HOST:-docker-cadence-1}:7933
    ```

The relationship between these variables:
- If `CADENCE_HOST` is set: `CADENCE_CLI_ADDRESS` becomes `${CADENCE_HOST}:7933`
- If not set: Falls back to `docker-cadence-1:7933`

Why two variables?
1. `CADENCE_CLI_ADDRESS` is the actual connection string used by Cadence clients
2. `CADENCE_HOST` is a convenience variable for easier configuration
3. The "CLI" in the name is historical (from Cadence CLI tool) but the address is used for all Cadence connections

Example configurations:
```bash
# Method 1: Set just the host
export CADENCE_HOST=my-cadence-server

# Method 2: Set full address directly
export CADENCE_CLI_ADDRESS=my-cadence-server:7933

# Method 3: Use docker-compose defaults
# No environment variables needed, uses docker-cadence-1:7933
```

Best Practice:
- Use `CADENCE_HOST` when you just need to change the hostname
- Use `CADENCE_CLI_ADDRESS` when you need to change both host and port
- Let the default values work when running everything in Docker Desktop

### Manual Workflow Commands

<details>
<summary>Manual Workflow Commands (Advanced Users Only - Click to Expand)</summary>

⚠️ **WARNING**: Manual workflow creation is NOT recommended for normal use. Use the Docker-based processor instead.
Only use these commands if you need to test specific scenarios or debug workflow behavior.

#### Required Variables
```bash
# Unique identifiers (must be UUID v4 format)
WORKFLOW_ID="11f67c55-27b5-43e8-b18b-bc8fbeaffe7a"  # Unique identifier for this workflow
USER_ID="a83b85d8-e294-e7b0-079e-0842c9f74dad"      # Unique identifier for the user
ORDER_ID="cb8be1a8-ec1b-0064-78f7-367d522355ae"     # Unique identifier for the order
RESTAURANT_ID="274acd37-77ea-65f7-e2e7-f81ace30937b" # Unique identifier for the restaurant

# Order content (must be valid JSON array)
ITEMS='["sandwich","grapes","chips"]'

# Restaurant decision (must be boolean)
DECISION="true"  # true for accept, false for reject

# Combined order JSON (must match schema)
ORDER_JSON="{\"id\":\"$ORDER_ID\",\"content\":$ITEMS}"
```

#### 1. Start Workflow
```bash
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow start \
    --workflow_id $WORKFLOW_ID \
    --tasklist HandleEatsOrderTaskList \
    --workflow_type HandleEatsOrderWorkflow::handleOrder \
    --execution_timeout 600 \
    --input "[$USER_ID, $ORDER_JSON, $RESTAURANT_ID]"
```

#### 2. Send Restaurant Decision
```bash
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow signal \
    --workflow_id $WORKFLOW_ID \
    --name HandleEatsOrderWorkflow::signalRestaurantDecision \
    --input $DECISION
```

#### Example Usage
```bash
# Set up variables
export WORKFLOW_ID=$(uuidgen)
export USER_ID=$(uuidgen)
export ORDER_ID=$(uuidgen)
export RESTAURANT_ID=$(uuidgen)
export ITEMS='["burger","fries","soda"]'
export DECISION="true"
export ORDER_JSON="{\"id\":\"$ORDER_ID\",\"content\":$ITEMS}"

# Start workflow
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow start \
    --workflow_id $WORKFLOW_ID \
    --tasklist HandleEatsOrderTaskList \
    --workflow_type HandleEatsOrderWorkflow::handleOrder \
    --execution_timeout 600 \
    --input "[$USER_ID, $ORDER_JSON, $RESTAURANT_ID]"

# Wait for workflow to initialize (at least 5 seconds)
sleep 5

# Send decision
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address ${CADENCE_HOST:-docker-cadence-1}:7933 \
    --do samples-domain \
    workflow signal \
    --workflow_id $WORKFLOW_ID \
    --name HandleEatsOrderWorkflow::signalRestaurantDecision \
    --input $DECISION
```

#### Important Notes
1. **Timing**: There must be a delay between starting the workflow and sending the signal
2. **UUID Format**: All IDs must be valid UUID v4 format
3. **JSON Format**: Order content must be a valid JSON array
4. **Network**: Commands must run on the same Docker network as Cadence
5. **Variables**: All environment variables must be properly set and escaped
6. **Monitoring**: Use Cadence Web UI to monitor workflow progress

⚠️ **Remember**: The automated processor handles all of these details automatically. Only use manual commands if absolutely necessary for testing or debugging.

</details>
