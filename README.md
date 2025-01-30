# Simple Stock Broker

This Stock Broker System uses Apaches ActiveMQ to simulate a broker communicating with multiple clients.

## Requirements
Before setting up the system, ensure you have the following installed:

- Java 22 (JDK 22 as per Maven configuration)

- Maven (for dependency management)

- ActiveMQ 6.1.2 (for messaging between components)

## Running Application
1. navigate to directory

     ```bash
     cd path/to/simple_stock_broker
     ```

2. build the project

     ```bash
     mvn clean compile
     ```

3. start each application separately
     ```bash
     mvn exec:java -Dexec.mainClass="de.local.simulate.address.jms.broker.JmsBrokerServer"
     mvn exec:java -Dexec.mainClass="de.local.simulate.address.jms.client.JmsBrokerClient"  # for each client separately)
     ```
4. follow the command line instructions for each client to simulate stock trading with the broker