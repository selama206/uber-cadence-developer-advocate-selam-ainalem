# Cadence Food Delivery Workflow Solution Walkthrough

## Architecture Overview

The solution implements a food delivery system using Cadence workflows, with a parent-child workflow pattern. Here's how it works:

```plaintext
+------------------------+     +------------------------+
|   Main Workflow        |     |   Child Workflow       |
|   (HandleEatsOrder)    |     |   (DeliverOrder)       |
+------------------------+     +------------------------+
| - Order Reception      |     | - Delivery Simulation  |
| - Restaurant Decision  |     | - Delivery Notification|
| - Food Preparation     |     | - Delivery Confirmation|
+------------------------+     +------------------------+
           |                              ^
           |                              |
           +------------------------------+
```

## Workflow Components

### 1. Main Workflow (`HandleEatsOrderWorkflow`)

The main workflow handles the entire order lifecycle:

```java
public class HandleEatsOrderWorkflowImpl implements HandleEatsOrderWorkflow {
    // Activity stub for order processing
    private final EatsActivities activities = Workflow.newActivityStub(
        EatsActivities.class,
        new ActivityOptions.Builder()
            .setScheduleToCloseTimeout(Duration.ofMinutes(1))
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToStartTimeout(Duration.ofSeconds(30))
            .setRetryOptions(new RetryOptions.Builder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(10))
                .setMaximumAttempts(3)
                .build())
            .build());

    private boolean restaurantDecision = false;
    private CompletablePromise<Void> signalPromise = Workflow.newPromise();
    private boolean signalReceived = false;

    @Override
    public String handleOrder(String userId, Order order, String restaurantId) {
        // 1. Validate inputs
        if (userId == null || order == null || restaurantId == null) {
            throw new IllegalArgumentException("Invalid input parameters");
        }

        // 2. Process initial order
        activities.processOrder("Your order received! " + String.join(", ", order.getContent()));

        // 3. Wait for restaurant decision with timeout
        boolean decisionReceived = Workflow.await(Duration.ofSeconds(60), 
            () -> signalPromise.isCompleted());

        // 4. Handle restaurant decision
        if (restaurantDecision) {
            // 5. Prepare order (simulated with sleep)
            Workflow.sleep(Duration.ofSeconds(3));

            // 6. Start delivery workflow
            DeliverOrderWorkflow deliveryWorkflow = Workflow.newChildWorkflowStub(
                DeliverOrderWorkflow.class,
                new ChildWorkflowOptions.Builder()
                    .setTaskList("DeliverOrderTaskList")
                    .setWorkflowId("deliver-order-" + order.getId())
                    .setExecutionStartToCloseTimeout(Duration.ofMinutes(10))
                    .setTaskStartToCloseTimeout(Duration.ofMinutes(1))
                    .build()
            );

            // 7. Execute delivery asynchronously
            Promise<String> deliveryPromise = Async.function(
                deliveryWorkflow::deliverOrder, 
                order.getId()
            );
            return deliveryPromise.get();
        }
        return "Order " + order.getId() + " was rejected by the restaurant";
    }

    @Override
    public void signalRestaurantDecision(boolean accepted) {
        this.restaurantDecision = accepted;
        this.signalReceived = true;
        this.signalPromise.complete(null);
    }
}
```

### 2. Child Workflow (`DeliverOrderWorkflow`)

The delivery workflow is implemented as a child workflow for better separation of concerns and scalability:

```java
public class DeliverOrderWorkflowImpl implements DeliverOrderWorkflow {
    private final EatsActivities activities = Workflow.newActivityStub(
        EatsActivities.class,
        new ActivityOptions.Builder()
            .setScheduleToCloseTimeout(Duration.ofMinutes(2))
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToStartTimeout(Duration.ofSeconds(10))
            .setRetryOptions(new RetryOptions.Builder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(3))
                .setMaximumAttempts(2)
                .build())
            .build());

    @Override
    public String deliverOrder(String orderId) {
        // 1. Simulate delivery time
        Workflow.sleep(Duration.ofSeconds(4));
        
        // 2. Send notifications
        activities.notifyOrderDelivered(orderId);
        activities.printDeliveryConfirmation(orderId);
        
        return "Order " + orderId + " delivered!";
    }
}
```

## Activities Implementation

The activities are implemented in separate interfaces and classes for better modularity. Here are the key activities used in the project:

### Order Processing Activities
1. **processOrder(String orderDetails)**
   - Used when an order is first received
   - Displays formatted order details including items
   - Called by the main workflow to acknowledge order receipt
   - Example: "Your order received! pizza, fries, soda"

2. **notifyOrderDelivered(String orderId)**
   - Called by the delivery workflow when delivery is complete
   - Sends delivery confirmation to the customer
   - Includes order ID for tracking
   - Example: "Order abc-123 delivered!"

3. **printDeliveryConfirmation(String orderId)**
   - Final confirmation of delivery
   - Provides delivery location information
   - Used as the last step in the delivery workflow
   - Example: "Your order is in front of your door!"

These activities are defined in the `EatsActivities` interface and implemented in `EatsActivityImpl`. They are registered with both the main worker and delivery worker to ensure consistent handling across the workflow.

Key characteristics of these activities:
- All activities include error handling and logging
- Activities are idempotent to handle potential retries
- Each activity has specific timeout and retry configurations
- Activities use standardized formatting for consistent user experience

## Workflow States and Transitions

### Main Workflow States:
1. **Order Received**
   - Validates input parameters
   - Processes initial order details
   - Displays order confirmation

2. **Awaiting Restaurant Decision**
   - Waits for signal with 60-second timeout
   - Handles signal through `signalRestaurantDecision` method
   - Tracks decision state using CompletablePromise

3. **Order Processing**
   - If accepted: Simulates 3-second preparation time
   - If rejected: Returns rejection message
   - Handles errors with proper logging

4. **Delivery Initiation**
   - Creates child workflow with specific options
   - Sets appropriate timeouts and retry policies
   - Manages workflow ID generation

### Child Workflow States:
1. **Delivery Started**
   - Initializes delivery process
   - Logs start of delivery

2. **Delivery in Progress**
   - Simulates 4-second delivery time
   - Handles potential delivery issues

3. **Delivery Completed**
   - Sends delivery notifications
   - Confirms delivery completion
   - Returns success message


## Script Processing

The `process_orders.sh` script provides automated order processing:
- Reads orders from CSV
- Generates unique IDs
- Manages workflow execution
- Handles signals and timing
- Provides detailed logging


## Explain the process of your learning and implementation

I began by reading the official Cadence documentation to understand its workflow concepts, and built on that knowledge through GitHub examples and tutorial videos on Cadence’s YouTube channel. I also integrated AI tools into my learning process to quickly clarify concepts and explore solutions more efficiently.

## Provide feedback about this process to explain how it can be improved

The Cadence YouTube videos linked on the documentation page are visually clear and helpful to follow. One suggestion: there is a video that demonstrates how to register a domain on youtube which isn’t currently linked on the Cadence page. Including that in the list would be a valuable addition for those setting up their environment for the first time.