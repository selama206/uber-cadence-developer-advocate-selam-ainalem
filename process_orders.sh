#!/bin/bash

# Make script exit on any error
set -e

# Add pipe failure detection
set -o pipefail

# Error handler
error_handler() {
    echo "Error occurred in script at line: $1"
    echo "Command that failed: $2"
    exit 1
}

# Set up error trap
trap 'error_handler ${LINENO} "$BASH_COMMAND"' ERR

# Add debugging
echo "Current directory: $(pwd)"
echo "Contents of /data:"
ls -la /data
echo "Argument received: $1"

# Check if CSV file is provided
if [ $# -ne 1 ]; then
    echo "Usage: $0 <csv_file>"
    echo "CSV format should be: items,status (status can be 'accept' or 'reject')"
    exit 1
fi

CSV_FILE=$1

# Add more debugging
echo "Looking for file at: $CSV_FILE"
echo "Does file exist? $(test -f "$CSV_FILE" && echo "Yes" || echo "No")"

# Check if file exists
if [ ! -f "$CSV_FILE" ]; then
    echo "Error: File $CSV_FILE not found"
    exit 1
fi

# Debug: Show file contents
echo "File contents:"
cat "$CSV_FILE"

# Function to generate UUID
generate_uuid() {
    if command -v uuidgen > /dev/null; then
        uuidgen
    else
        # Fallback to random if uuidgen is not available
        echo $(cat /dev/urandom | tr -dc 'a-f0-9' | fold -w 32 | head -n 1 | sed 's/\(..\)/\1/g')
    fi
}

# Test Docker and Cadence connectivity before processing
echo "Testing Docker connectivity..."
docker ps || { echo "Failed to connect to Docker daemon"; exit 1; }

echo "Testing Cadence connectivity..."
docker run --rm \
    --network=docker_default \
    ubercadence/cli:master \
    --address docker-cadence-1:7933 \
    --do samples-domain \
    domain describe || { echo "Failed to connect to Cadence"; exit 1; }

# Process each line in the CSV
# Skip header line
echo "Starting to process lines..."
echo "Debug: About to start while loop"
tail -n +2 "$CSV_FILE" | { while IFS=, read -r items status || [ -n "$items" ]; do
    echo "Debug: Inside while loop"
    echo "Debug: Raw line data - items: |$items|, status: |$status|"
    
    # Generate UUIDs for order_id, user_id, and restaurant_id
    order_id=$(generate_uuid)
    user_id=$(generate_uuid)
    restaurant_id=$(generate_uuid)
    
    # Remove quotes if present
    items=$(echo "$items" | tr -d '"')  # Remove any existing quotes
    status=$(echo "$status" | tr -d '"' | tr -d '\r' | tr '[:upper:]' '[:lower:]')  # Normalize status and remove carriage return
    
    echo "After cleanup - items: '$items', status: '$status'"
    
    # Validate status
    if [ "$status" != "accept" ] && [ "$status" != "reject" ]; then
        echo "Invalid status '$status' for order. Skipping..."
        continue
    fi
    
    items_array="[$(echo "$items" | tr ';' ',' | awk -F',' '{for(i=1;i<=NF;i++){if(i>1)printf ",";printf "\"%s\"", $i}}')"
    items_array="${items_array}]"  # Close the array
    
    echo "Starting workflow for order: $order_id"
    echo "User ID: $user_id"
    echo "Items: $items_array"
    echo "Restaurant ID: $restaurant_id"
    echo "Restaurant Decision: $status"
    
    # Create the order JSON object
    order_json="{\"id\":\"$order_id\",\"content\":$items_array}"
    echo "Order JSON: $order_json"
    
    # Start the workflow and capture the workflow ID
    echo "Running workflow command..."
    workflow_output=$(docker run --rm \
        --network=docker_default \
        ubercadence/cli:master \
        --address docker-cadence-1:7933 \
        --do samples-domain \
        workflow start \
        --tasklist HandleEatsOrderTaskList \
        --workflow_type HandleEatsOrderWorkflow::handleOrder \
        --execution_timeout 1200 \
        --input "[\"$user_id\",$order_json,\"$restaurant_id\"]" 2>&1) || { echo "Failed to start workflow"; exit 1; }
    
    echo "Workflow command output: $workflow_output"
    workflow_id=$(echo "$workflow_output" | grep -oP "Workflow Id: \K[a-f0-9-]+")
    
    if [ -z "$workflow_id" ]; then
        echo "Failed to get workflow ID. Skipping this order."
        exit 1
    fi
    
    echo "Got workflow ID: $workflow_id"
    
    # Wait longer for workflow to initialize
    echo "Waiting for workflow to initialize..."
    sleep 30  # Increased from 15 to 30 seconds
    
    # Convert status to boolean for signal
    signal_value="false"
    if [ "$status" = "accept" ]; then
        signal_value="true"
    fi
    
    # Send restaurant decision
    echo "Sending restaurant decision ($status) for workflow: $workflow_id"
    max_retries=3
    retry_count=0
    signal_sent=false
    
    while [ $retry_count -lt $max_retries ] && [ "$signal_sent" = false ]; do
        echo "Sending signal attempt $((retry_count + 1))..."
        signal_output=$(docker run --rm \
            --network=docker_default \
            ubercadence/cli:master \
            --address docker-cadence-1:7933 \
            --do samples-domain \
            workflow signal \
            --workflow_id "$workflow_id" \
            --name HandleEatsOrderWorkflow::signalRestaurantDecision \
            --input "$signal_value" 2>&1) || { echo "Failed to send signal"; exit 1; }
        
        echo "Signal command output: $signal_output"
        
        if [[ $signal_output == *"Signal workflow succeeded"* ]]; then
            echo "Signal sent successfully"
            signal_sent=true
        else
            echo "Failed to send signal, attempt $((retry_count + 1)) of $max_retries"
            retry_count=$((retry_count + 1))
            if [ $retry_count -lt $max_retries ]; then
                sleep 5  # Wait 5 seconds before retrying
            fi
        fi
    done
    
    if [ "$signal_sent" = false ]; then
        echo "Failed to send signal after $max_retries attempts. Skipping this order."
        exit 1
    fi
    
    # Wait longer for workflow to complete (3s prep + 4s delivery + buffer)
    echo "Waiting for workflow to complete..."
    sleep 30  # Increased from 20 to 30 seconds
    
    echo "Completed processing order: $order_id"
    echo "----------------------------------------"
done }

echo "All orders processed!" 