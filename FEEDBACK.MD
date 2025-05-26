# Building Your First Cadence Workflow: From Local Docker to Cadence Web

> A beginner-friendly, hands-on guide for running your own Cadence workflow locally, complete with working code, gotchas, and tips to avoid common frustrations.

---

## TL;DR

This article shows you how to:

- Run a working Java Cadence workflow that simulates a food delivery system
- Use Docker + Cadence Web to inspect your workflow
- Handle signals, timeouts, and child workflows
- Implement parent-child workflow patterns
- Process orders through a shell script interface

With code examples, gotchas, and things I wish I'd known.

---

## Demo Overview

Here's what we built: a comprehensive food delivery workflow system.

1. The main workflow (HandleEatsOrder) receives an order
2. It waits for a restaurant decision signal (with timeout)
3. If accepted, it simulates food preparation (3 seconds)
4. It spawns a child workflow (DeliverOrder) for delivery handling
5. The delivery workflow simulates delivery (4 seconds) and confirms completion

You can monitor the entire process in **Cadence Web**, running via Docker.

---

## Architecture Overview

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

## Key Components

### 1. Main Workflow (HandleEatsOrderWorkflow)
- Manages the complete order lifecycle
- Implements restaurant decision signal handling
- Uses CompletablePromise for signal coordination
- Includes configurable timeouts and retry policies

### 2. Child Workflow (DeliverOrderWorkflow)
- Handles delivery-specific logic
- Manages delivery notifications
- Implements delivery confirmation
- Uses separate activity stubs for isolation

### 3. Activities
- processOrder: Initial order processing and validation
- notifyOrderDelivered: Delivery status notifications
- printDeliveryConfirmation: Final delivery confirmation

### 4. Script Processing
- Automated order processing through shell scripts
- CSV-based order management
- Unique ID generation
- Workflow execution management

---

## Implementation Highlights

### Signal Handling
```java
private CompletablePromise<Void> signalPromise = Workflow.newPromise();
private boolean signalReceived = false;

@Override
public void signalRestaurantDecision(boolean accepted) {
    this.restaurantDecision = accepted;
    this.signalReceived = true;
    this.signalPromise.complete(null);
}
```

### Child Workflow Management
```java
DeliverOrderWorkflow deliveryWorkflow = Workflow.newChildWorkflowStub(
    DeliverOrderWorkflow.class,
    new ChildWorkflowOptions.Builder()
        .setTaskList("DeliverOrderTaskList")
        .setWorkflowId("deliver-order-" + order.getId())
        .setExecutionStartToCloseTimeout(Duration.ofMinutes(10))
        .build()
);
```

---

## Gotchas and Tips

### 1. Workflow Design
- Use CompletablePromise for signal coordination
- Implement proper timeout handling
- Structure activities for reusability
- Consider parent-child workflow patterns for complex processes

### 2. Error Handling
- Implement proper retry policies
- Use appropriate timeout configurations
- Handle signal timing issues
- Manage workflow state transitions

### 3. Testing and Debugging
- Use the provided test framework
- Monitor workflow execution in Cadence Web
- Check activity completion status
- Verify signal reception and timing

### 4. Development Setup
- Ensure proper Docker configuration
- Set up appropriate task lists
- Configure worker options correctly
- Use the process_orders.sh script for testing

---

## Common Issues and Solutions

### Signal Timing
- **Issue**: Signal not received within timeout
- **Solution**: Adjust timeout duration in workflow options

### Activity Execution
- **Issue**: Activities not completing
- **Solution**: Check worker registration and task list configuration

### Child Workflow Communication
- **Issue**: Parent-child workflow coordination
- **Solution**: Use proper workflow IDs and task lists

### Script Processing
- **Issue**: Order processing automation
- **Solution**: Verify CSV format and script permissions

---

## Future Improvements

### 1. Technical Enhancements
- Add metrics collection
- Implement more sophisticated retry policies
- Enhance error handling
- Add comprehensive logging

### 2. Feature Additions
- Real-time order tracking
- Multiple restaurant support
- Batch order processing
- Advanced delivery routing

### 3. Documentation
- API documentation
- Configuration guides
- Deployment scenarios
- Performance tuning

---

## Conclusion

This implementation demonstrates a practical application of Cadence workflows in a food delivery system. It showcases:
- Parent-child workflow patterns
- Signal handling
- Activity implementation
- Script automation
- Error handling and reliability

# Feedback and Improvements

## Latest Changes

1. **Timeout and Retry Configuration**

   - Standardized timeout and retry settings across all workflows and activities
   - Main workflow: 10min execution, 1min task, 2 retries
   - Child workflow: 10min execution, 1min task, 2 retries
   - Activities: 2min schedule-to-close, 30s start-to-close, 10s schedule-to-start, 2 retries

2. **Build Process**

   - Added `--no-cache` flag to all build commands to ensure latest changes are applied
   - Updated README with new build instructions
   - Improved build reliability and consistency

3. **Child Workflow Handling**

   - Improved child workflow result handling using `Async.function()`
   - Added proper Promise handling for child workflow results
   - Enhanced error handling and logging

4. **Message Handling**
   - Removed duplicate "front door" messages
   - Standardized message format across workflows
   - Improved message clarity and consistency

## Previous Improvements

1. **Order Processing**

   - Added validation for order items
   - Improved error handling for invalid orders
   - Enhanced logging for order processing steps

2. **Workflow Structure**

   - Separated delivery into child workflow
   - Added proper workflow ID handling
   - Improved workflow state management

3. **Activity Implementation**

   - Enhanced activity error handling
   - Added detailed logging
   - Improved activity timeout handling

4. **Signal Handling**
   - Added proper signal timeout handling
   - Improved signal validation
   - Enhanced signal error handling

## Known Issues

1. **Build Process**

   - Docker Compose version warning (obsolete version attribute)
   - Need to use `--no-cache` for reliable builds

2. **Workflow Execution**

   - Child workflow deserialization issues with void return type
   - Need to use String return type for child workflows

3. **Logging**
   - Some duplicate log messages
   - Need to standardize log levels

## Future Improvements

1. **Build Process**

   - Update Docker Compose configuration
   - Add build caching strategy
   - Improve build performance

2. **Workflow Execution**

   - Add workflow versioning
   - Implement workflow migration
   - Add workflow metrics

3. **Logging**

   - Implement structured logging
   - Add log aggregation
   - Improve log filtering

4. **Testing**
   - Add unit tests
   - Add integration tests
   - Add performance tests

## Best Practices

1. **Build Process**

   - Always use `--no-cache` for builds
   - Rebuild both worker and processor when making changes
   - Check build logs for errors

2. **Workflow Development**

   - Use proper timeout and retry configurations
   - Handle child workflow results properly
   - Implement proper error handling

3. **Activity Development**

   - Use appropriate timeout settings
   - Implement proper retry logic
   - Add detailed logging

4. **Signal Handling**
   - Use proper signal timeouts
   - Validate signal data
   - Handle signal errors properly

# Feedback for Uber Cadence Team

## Documentation Improvements

### 1. Getting Started Experience
- **Challenge**: Initial setup complexity for beginners
- **Feedback**: 
  - Create a "Quick Start" guide with a simple, complete example
  - Provide a step-by-step tutorial for setting up the development environment
  - Include troubleshooting sections for common setup issues
  - Add more visual diagrams explaining workflow concepts

### 2. Workflow Concepts
- **Challenge**: Understanding core Cadence concepts
- **Feedback**:
  - Add more real-world examples of when to use parent-child workflows
  - Provide clear guidelines on choosing between activities vs child workflows
  - Include decision trees for common architectural choices
  - Create visual guides for workflow lifetime and state transitions

### 3. Signal Handling
- **Challenge**: Understanding signal timing and coordination
- **Feedback**:
  - Add examples of different signal patterns (polling, async, etc.)
  - Provide best practices for timeout configurations
  - Include common pitfalls and how to avoid them
  - Create debugging guides for signal-related issues

### 4. Error Handling
- **Challenge**: Implementing robust error handling
- **Feedback**:
  - Provide more examples of error handling patterns
  - Include guidelines for retry policies
  - Add examples of handling different types of failures
  - Create troubleshooting guides for common errors

## Development Experience

### 1. Local Development
- **Challenge**: Setting up local development environment
- **Feedback**:
  - Provide Docker Compose templates for different scenarios
  - Include debugging tips for common Docker issues
  - Add guidelines for development workflow
  - Create checklists for environment setup

### 2. Testing
- **Challenge**: Writing tests for workflows
- **Feedback**:
  - Add more examples of unit testing workflows
  - Include guidelines for integration testing
  - Provide examples of testing timeouts and retries
  - Create templates for common test scenarios

### 3. Debugging
- **Challenge**: Debugging workflow issues
- **Feedback**:
  - Improve documentation on using Cadence Web UI
  - Add examples of common debugging scenarios
  - Include logging best practices
  - Create troubleshooting decision trees

### 4. Performance
- **Challenge**: Understanding performance implications
- **Feedback**:
  - Add guidelines for workflow optimization
  - Include examples of performance monitoring
  - Provide benchmarks for common operations
  - Create performance tuning guides

## SDK Improvements

### 1. Java SDK
- **Challenge**: Working with Java SDK
- **Feedback**:
  - Add more code examples in documentation
  - Include common patterns and anti-patterns
  - Provide guidelines for SDK version upgrades
  - Create migration guides between versions

### 2. Error Messages
- **Challenge**: Understanding error messages
- **Feedback**:
  - Improve error message clarity
  - Add troubleshooting guides for common errors
  - Include examples of resolving specific errors
  - Create error message reference guide

### 3. API Design
- **Challenge**: Understanding API design choices
- **Feedback**:
  - Add more context to API documentation
  - Include examples of API usage patterns
  - Provide guidelines for API versioning
  - Create API best practices guide

## Community Support

### 1. Learning Resources
- **Challenge**: Finding learning resources
- **Feedback**:
  - Create a centralized learning resource hub
  - Add more video tutorials
  - Include case studies of real-world implementations
  - Create a beginner's guide to contributing

### 2. Community Engagement
- **Challenge**: Getting help from the community
- **Feedback**:
  - Improve documentation on where to get help
  - Create templates for asking questions
  - Add guidelines for reporting issues
  - Create a knowledge base of common solutions

## Specific Feature Requests

1. **Development Tools**
   - IDE plugins for workflow visualization
   - CLI tools for workflow management
   - Development time workflow simulators
   - Local development helper utilities

2. **Documentation**
   - Interactive tutorials
   - More comprehensive API reference
   - Pattern library with examples
   - Troubleshooting decision trees

3. **Testing Tools**
   - Workflow testing frameworks
   - Performance testing tools
   - Integration testing helpers
   - Mock data generators

4. **Monitoring**
   - Better visualization tools
   - Performance monitoring utilities
   - Debugging helpers
   - Log analysis tools

These suggestions are based on hands-on experience building this food delivery workflow system and aim to improve the experience for future developers working with Cadence.
