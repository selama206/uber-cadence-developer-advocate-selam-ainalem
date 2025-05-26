package eatsworkflow;

import com.uber.cadence.workflow.WorkflowMethod;
import com.uber.cadence.common.MethodRetry;

public interface DeliverOrderWorkflow {
    @WorkflowMethod(
        executionStartToCloseTimeoutSeconds = 600, // 10 minutes timeout
        taskStartToCloseTimeoutSeconds = 60 // 1 minute task timeout
    )
    @MethodRetry(initialIntervalSeconds = 1, maximumIntervalSeconds = 3, maximumAttempts = 2)
    String deliverOrder(String orderId);
}
