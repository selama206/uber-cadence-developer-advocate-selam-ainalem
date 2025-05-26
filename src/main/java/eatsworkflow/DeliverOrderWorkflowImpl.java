package eatsworkflow;

import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.activity.ActivityOptions;
import java.time.Duration;
import org.slf4j.Logger;

public class DeliverOrderWorkflowImpl implements DeliverOrderWorkflow {
    private static final Logger logger = Workflow.getLogger(DeliverOrderWorkflowImpl.class);

    private final EatsActivities activities = Workflow.newActivityStub(
        EatsActivities.class,
        new ActivityOptions.Builder()
            .setScheduleToCloseTimeout(Duration.ofMinutes(2))
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToStartTimeout(Duration.ofSeconds(10))
            .setRetryOptions(new com.uber.cadence.common.RetryOptions.Builder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(3))
                .setMaximumAttempts(2)
                .build())
            .build());

    @Override
    public String deliverOrder(String orderId) {
        try {
            logger.info("Starting delivery for order: {}", orderId);
            
            // Sleep for 4 seconds to simulate delivery time
            logger.info("Simulating delivery time for order: {}", orderId);
            Workflow.sleep(Duration.ofSeconds(4));
            
            // Print delivery confirmation
            logger.info("Delivery completed for order: {}", orderId);
            activities.notifyOrderDelivered(orderId);
            activities.printDeliveryConfirmation(orderId);
            
            logger.info("Order {} delivered!", orderId);
            return "Order " + orderId + " delivered!";
        } catch (Exception e) {
            logger.error("Error in delivery workflow for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }
}
