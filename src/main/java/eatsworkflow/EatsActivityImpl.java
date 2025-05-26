package eatsworkflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EatsActivityImpl implements EatsActivities {
    private static final Logger logger = LoggerFactory.getLogger(EatsActivityImpl.class);

    @Override
    public void processOrder(String orderDetails) {
        try {
            logger.info("Processing order: {}", orderDetails);
            System.out.println("\nORDER UPDATE:\n" + 
                             "============================\n" + 
                             orderDetails + 
                             "\n============================\n");
        } catch (Exception e) {
            logger.error("Error processing order: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void notifyOrderDelivered(String orderId) {
        try {
            String message = "Order " + orderId + " delivered!";
            logger.info(message);
            System.out.println("\nDELIVERY NOTIFICATION:\n" +
                             "============================\n" +
                             message +
                             "\n============================\n");
        } catch (Exception e) {
            logger.error("Error notifying delivery: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void printDeliveryConfirmation(String orderId) {
        try {
            String message = "Your order is in front of your door!";
            logger.info(message);
            System.out.println("\nDELIVERY CONFIRMATION:\n" +
                             "============================\n" +
                             message +
                             "\n============================\n");
        } catch (Exception e) {
            logger.error("Error printing delivery confirmation: {}", e.getMessage());
            throw e;
        }
    }
} 