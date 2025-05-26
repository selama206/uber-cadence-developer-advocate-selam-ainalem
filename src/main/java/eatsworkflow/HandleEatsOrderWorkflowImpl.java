package eatsworkflow;

import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.workflow.ChildWorkflowOptions;
import com.uber.cadence.workflow.CompletablePromise;
import java.time.Duration;
import org.slf4j.Logger;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Async;

public class HandleEatsOrderWorkflowImpl implements HandleEatsOrderWorkflow {
    private static final Logger logger = Workflow.getLogger(HandleEatsOrderWorkflowImpl.class);

    private final EatsActivities activities = Workflow.newActivityStub(
        EatsActivities.class,
        new ActivityOptions.Builder()
            .setScheduleToCloseTimeout(Duration.ofMinutes(1))
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToStartTimeout(Duration.ofSeconds(30))
            .setRetryOptions(new com.uber.cadence.common.RetryOptions.Builder()
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
        try {
         
            logger.info("Starting workflow execution for order: {}", order != null ? order.getId() : "null");
            logger.info("Received parameters - userId: {}, order: {}, restaurantId: {}", 
                userId, order, restaurantId);

            // Validate inputs with more detailed error messages
            if (userId == null || userId.trim().isEmpty()) {
                logger.error("Invalid userId: {}", userId);
                throw new IllegalArgumentException("User ID cannot be null or empty");
            }
            if (order == null) {
                logger.error("Order is null");
                throw new IllegalArgumentException("Order cannot be null");
            }
            if (order.getId() == null || order.getId().trim().isEmpty()) {
                logger.error("Order ID is null or empty");
                throw new IllegalArgumentException("Order ID cannot be null or empty");
            }
            if (order.getContent() == null) {
                logger.error("Order content is null");
                throw new IllegalArgumentException("Order content cannot be null");
            }
            if (restaurantId == null || restaurantId.trim().isEmpty()) {
                logger.error("Invalid restaurantId: {}", restaurantId);
                throw new IllegalArgumentException("Restaurant ID cannot be null or empty");
            }

            // Print order received message with details
            logger.info("Your order received!");
            activities.processOrder("Your order received! " + String.join(", ", order.getContent()));

            logger.info("Waiting for restaurant decision...");
            
            // Wait for restaurant decision with timeout
            try {
                boolean decisionReceived = Workflow.await(Duration.ofSeconds(60), () -> signalPromise.isCompleted());
                if (!decisionReceived) {
                    logger.error("Timeout waiting for restaurant decision");
                    throw new RuntimeException("Timeout waiting for restaurant decision");
                }
                if (!signalReceived) {
                    logger.error("No signal received before timeout");
                    throw new RuntimeException("No signal received before timeout");
                }
                logger.info("Restaurant decision received: {}", restaurantDecision);
            } catch (Exception e) {
                logger.error("Error waiting for restaurant decision", e);
                throw e;
            }
            
            if (!restaurantDecision) {
                logger.info("Order {} was rejected by the restaurant", order.getId());
                activities.processOrder(String.format("Order %s was rejected by the restaurant\nItems: %s", 
                    order.getId(), order.getContent()));
                return "Order " + order.getId() + " was rejected by the restaurant";
            }

        
            Workflow.sleep(Duration.ofSeconds(3));
        

            logger.info("Starting delivery child workflow...");
            
            ChildWorkflowOptions childOptions = new ChildWorkflowOptions.Builder()
                .setTaskList("DeliverOrderTaskList")
                .setWorkflowId("deliver-order-" + order.getId())
                .setExecutionStartToCloseTimeout(Duration.ofMinutes(10))
                .setTaskStartToCloseTimeout(Duration.ofMinutes(1))
                .build();

            logger.info("Initiating delivery for order: {} with options: {}", order.getId(), childOptions);
            
            try {
                // Execute child workflow and wait for completion
                DeliverOrderWorkflow deliveryWorkflow = Workflow.newChildWorkflowStub(
                    DeliverOrderWorkflow.class,
                    childOptions
                );
                
                logger.info("Starting child workflow for delivery of order: {}", order.getId());
                // Start the child workflow asynchronously and wait for its completion
                Promise<String> deliveryPromise = Async.function(deliveryWorkflow::deliverOrder, order.getId());
                String deliveryResult = deliveryPromise.get();
                
                logger.info("Child workflow completed successfully for order: {} with result: {}", order.getId(), deliveryResult);
                logger.info("Main workflow completed for order: {}", order.getId());
                return "Your order is in front of your door!";
            } catch (Exception e) {
                logger.error("Delivery failed for order {}: {}", order.getId(), e.getMessage());
                activities.processOrder(String.format("Order %s delivery failed: %s\nItems: %s", 
                    order.getId(), e.getMessage(), order.getContent()));
                return "Order " + order.getId() + " delivery failed: " + e.getMessage();
            }
        } catch (Exception e) {
            logger.error("Error in handleOrder workflow: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void signalRestaurantDecision(boolean accepted) {
        logger.info("Received restaurant decision signal. Decision: {}", accepted ? "ACCEPTED" : "REJECTED");
        if (signalPromise.isCompleted()) {
            logger.warn("Signal received after promise was already completed. Ignoring signal.");
            return;
        }
        this.restaurantDecision = accepted;
        this.signalReceived = true;
        this.signalPromise.complete(null);
        logger.info("Signal processed and promise completed. Workflow will continue execution with decision: {}", accepted);
    }
}
