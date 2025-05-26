package eatsworkflow;

import com.uber.cadence.activity.ActivityMethod;

public interface EatsActivities {
    @ActivityMethod
    void processOrder(String orderDetails);

    @ActivityMethod
    void notifyOrderDelivered(String orderId);

    @ActivityMethod
    void printDeliveryConfirmation(String orderId);
} 