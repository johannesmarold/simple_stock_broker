package de.local.simulate.address.jms.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
//import javax.jms.Connection;
//import javax.jms.JMSException;
//import javax.jms.Message;
//import javax.jms.MessageConsumer;
//import javax.jms.MessageListener;
//import javax.jms.MessageProducer;
//import javax.jms.ObjectMessage;

import de.local.simulate.address.jms.common.*;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;


public class JmsBrokerClient {
    private double budget = 100;

    private final Map<String, Integer> purchasedStocks = new HashMap<>();
    //private int budget = 50 + new java.util.Random().nextInt(101);
    private final String brokerAddress = "tcp://localhost:61616";
    private final jakarta.jms.Connection connection;
    private final Session session;
    private final String clientName;
    private Queue inputQueue, outputQueue;

    private final MessageConsumer consumer;

    private final HashMap<String, MessageConsumer> subscribedTopics = new HashMap<>();

    public class BrokerResponseListener implements MessageListener {

        @Override
        public void onMessage(Message message) {
            try {
                Destination destination = message.getJMSDestination();
                if (destination instanceof Queue) {
                    // TODO check correct usage of both queues
                    System.out.println(((Queue) destination).getQueueName());
                    try {
                        if (message instanceof ObjectMessage) {
                            ObjectMessage objectMessage = (ObjectMessage) message;
                            handleObjectMessage(objectMessage);
                        } else {
                            System.out.println("Received message of unsupported type: " + message.getClass().getName());
                        }
                    } catch (JMSException e) {
                        System.err.println("Error processing message: " + e.getMessage());
                    }
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }

        private void handleObjectMessage(ObjectMessage objectMessage) throws JMSException {
            try {
                Object body = objectMessage.getObject();
                //TODO check correct update of budget and purchasedStocks
                if (body instanceof ResponseMessage) {
                    ResponseMessage response = (ResponseMessage) body;
                    if (response.getResult() == -1) {
                        System.out.println("transaction not successful.");
                    }
                    else {
                        if (response.getAction().equals("buy")) {
                            budget = budget - response.getResult() * response.getPrice();
                            purchasedStocks.put(response.getStockName(), purchasedStocks.getOrDefault(response.getStockName(), 0) + response.getResult());
                            System.out.println("purchase successful, updated budget:  " + budget);

                        }
                        if (response.getAction().equals("sell")) {
                            budget = budget + response.getResult() * response.getPrice();
                            purchasedStocks.put(response.getStockName(), purchasedStocks.get(response.getStockName()) - response.getResult());
                            System.out.println("sale successful, updated budget: " + budget);
                        }
                    }
                }
                // handle list response
                else if (body instanceof ListMessage listMessage) {
                    ArrayList<Stock> stocks = new ArrayList<>(listMessage.getStocks());
                    System.out.println("Received list: " + stocks);
                }
                // is this branch necessary? I think it is handled in watch()
                else if (body instanceof TextMessage textMessage) {
                    System.out.println("Received Message from Topic: " + textMessage.getText());
                }
                // handle unknown response type
                else {
                    System.out.println("Received unknown object message: " + body.getClass().getName());
                }
            } catch (JMSException | ClassCastException e) {
                System.err.println("Error handling object message: " + e.getMessage());
            }
        }
    }
    public JmsBrokerClient(String clientName) throws JMSException {
        this.clientName = clientName;
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerAddress);
        connectionFactory.setTrustAllPackages(true);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        registerClient(clientName);
        inputQueue = session.createQueue(clientName + ".input");
        outputQueue = session.createQueue(clientName + ".output");
        consumer = session.createConsumer(outputQueue);
        consumer.setMessageListener(new BrokerResponseListener());
    }

    private void registerClient(String clientName) throws JMSException {
        MessageProducer producer = session.createProducer(session.createQueue("registrationQueue"));
        RegisterMessage registerMessage = new RegisterMessage(clientName);
        ObjectMessage objectMessage = session.createObjectMessage(registerMessage);
        producer.send(objectMessage);
    }

    public void requestList() throws JMSException {
        MessageProducer producer = session.createProducer(inputQueue);
        RequestListMessage requestListMessage = new RequestListMessage();
        ObjectMessage objectMessage = session.createObjectMessage(requestListMessage);
        producer.send(objectMessage);
    }
    
    public void buy(String stockName, int amount) throws JMSException {
        MessageProducer producer = session.createProducer(inputQueue);
        BuyMessage buyMessage = new BuyMessage(stockName, amount, budget);
        ObjectMessage objectMessage = session.createObjectMessage(buyMessage);
        producer.send(objectMessage);
    }
    
    public int sell(String stockName, int amount) throws JMSException {
        if (purchasedStocks.containsKey(stockName)) {
            int stockAmount = purchasedStocks.get(stockName);
            if (stockAmount >= amount) {
                MessageProducer producer = session.createProducer(inputQueue);
                SellMessage sellMessage = new SellMessage(stockName, amount);
                ObjectMessage objectMessage = session.createObjectMessage(sellMessage);
                producer.send(objectMessage);
                return 1;
            }
        }
        return -1;
    }
    
    public void watch(String stockName) throws JMSException {
        Topic stockTopic = session.createTopic(stockName);
        MessageConsumer topicConsumer = session.createConsumer(stockTopic);
        topicConsumer.setMessageListener(message -> {
            try {
                Destination destination = message.getJMSDestination();
                if (destination instanceof Topic) {
                    if (message instanceof TextMessage textMessage) {
                        try {
                            System.out.println("WATCH MSG: " + textMessage.getText());
                        } catch (JMSException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        });
        subscribedTopics.put(stockName, topicConsumer);
    }
    
    public void unwatch(String stockName) throws JMSException {
        subscribedTopics.get(stockName).close();
        subscribedTopics.remove(stockName);
    }
    
    public void quit() throws JMSException {
        session.close();
        connection.close();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter the client name:");
            String clientName = reader.readLine();
            
            JmsBrokerClient client = new JmsBrokerClient(clientName);
            
            boolean running = true;
            while(running) {
                System.out.println("Enter command:");
                String[] task = reader.readLine().split(" ");
                
                synchronized(client) {
                    switch(task[0].toLowerCase()) {
                        case "quit":
                            client.quit();
                            System.out.println("Bye bye");
                            running = false;
                            break;
                        case "list":
                            client.requestList();
                            break;
                        case "buy":
                            if (task.length == 3) {
                                try {
                                    int amount = Integer.parseInt(task[2]);
                                    client.buy(task[1], amount);
                                } catch (NumberFormatException e) {
                                    System.out.println("Amount must be a valid integer.");
                                }
                            } else {
                                System.out.println("Correct usage: buy [stock] [amount]");
                            }
                            break;
                        case "sell":
                            if(task.length == 3) {
                                try {
                                    int amount = Integer.parseInt(task[2]);
                                    int enough = client.sell(task[1], amount);
                                    if (enough == -1) {
                                        System.out.println("Client does not possess enough stocks to sell.");
                                    }
                                } catch (NumberFormatException e) {
                                    System.out.println("Amount must be a valid integer.");
                                }
                            } else {
                                System.out.println("Correct usage: sell [stock] [amount]");
                            }
                            break;
                        case "watch":
                            if(task.length == 2) {
                                client.watch(task[1]);
                            } else {
                                System.out.println("Correct usage: watch [stock]");
                            }
                            break;
                        case "unwatch":
                            if(task.length == 2) {
                                client.unwatch(task[1]);
                            } else {
                                System.out.println("Correct usage: watch [stock]");
                            }
                            break;
                        default:
                            System.out.println("Unknown command. Try one of:");
                            System.out.println("quit, list, buy, sell, watch, unwatch");
                    }
                }
            }
            
        } catch (JMSException | IOException ex) {
            Logger.getLogger(JmsBrokerClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
}
