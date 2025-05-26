package eatsworkflow;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.worker.WorkerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerStarter {
    private static final Logger logger = LoggerFactory.getLogger(WorkerStarter.class);
    private static final String DOMAIN = "samples-domain";
    private static final String MAIN_TASK_LIST = "HandleEatsOrderTaskList";
    private static final String DELIVERY_TASK_LIST = "DeliverOrderTaskList";
    
    // Get Cadence address from environment variable with fallback
    private static String[] getCadenceHostAndPort() {
        // First check CADENCE_CLI_ADDRESS for backward compatibility
        String cadenceAddress = System.getenv("CADENCE_CLI_ADDRESS");
        if (cadenceAddress == null || cadenceAddress.isEmpty()) {
            // If not set, construct from CADENCE_HOST with fallback
            String cadenceHost = System.getenv().getOrDefault("CADENCE_HOST", "docker-cadence-1");
            cadenceAddress = cadenceHost + ":7933";
            logger.info("Using Cadence address constructed from CADENCE_HOST: {}", cadenceAddress);
        } else {
            logger.info("Using Cadence address from CADENCE_CLI_ADDRESS: {}", cadenceAddress);
        }
        
        String[] parts = cadenceAddress.split(":");
        if (parts.length != 2) {
            String defaultAddress = "docker-cadence-1:7933";
            logger.warn("Invalid address format: '{}', using default: {}", cadenceAddress, defaultAddress);
            return new String[]{"docker-cadence-1", "7933"};
        }
        
        return parts;
    }

    public static void main(String[] args) {
        try {
            String[] hostAndPort = getCadenceHostAndPort();
            String host = hostAndPort[0];
            int port = Integer.parseInt(hostAndPort[1]);
            
            logger.info("Starting worker with host: {} and port: {}", host, port);
            
            // Configure ObjectMapper for lenient deserialization
            JacksonDataConverter dataConverter = new JacksonDataConverter();
            
            // Create workflow service client with explicit host and port
            ClientOptions clientOptions = ClientOptions.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();
            
            WorkflowServiceTChannel service = new WorkflowServiceTChannel(clientOptions);
            WorkflowClient workflowClient = WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder()
                    .setDomain(DOMAIN)
                    .setDataConverter(dataConverter)
                    .build()
            );

            logger.info("Created workflow client for domain: {}", DOMAIN);

            // Create worker factory
            WorkerFactory factory = WorkerFactory.newInstance(workflowClient);

            // Create main worker with increased concurrency
            Worker mainWorker = factory.newWorker(
                MAIN_TASK_LIST,
                WorkerOptions.newBuilder()
                    .setMaxConcurrentActivityExecutionSize(10)
                    .setMaxConcurrentWorkflowExecutionSize(10)
                    .build()
            );

            logger.info("Created main worker for task list: {}", MAIN_TASK_LIST);

            // Register main workflow and activities
            mainWorker.registerWorkflowImplementationTypes(HandleEatsOrderWorkflowImpl.class);
            mainWorker.registerActivitiesImplementations(new EatsActivityImpl());

            // Create delivery worker with increased concurrency
            Worker deliveryWorker = factory.newWorker(
                DELIVERY_TASK_LIST,
                WorkerOptions.newBuilder()
                    .setMaxConcurrentActivityExecutionSize(10)
                    .setMaxConcurrentWorkflowExecutionSize(10)
                    .build()
            );

            logger.info("Created delivery worker for task list: {}", DELIVERY_TASK_LIST);

            // Register delivery workflow and activities
            deliveryWorker.registerWorkflowImplementationTypes(DeliverOrderWorkflowImpl.class);
            deliveryWorker.registerActivitiesImplementations(new EatsActivityImpl());

            // Start all workers
            logger.info("Starting all workers via factory.start()");
            factory.start();

            logger.info("Workers started successfully. Waiting for tasks...");
            
            // Keep the worker running
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error starting worker", e);
            System.exit(1);
        }
    }
}
