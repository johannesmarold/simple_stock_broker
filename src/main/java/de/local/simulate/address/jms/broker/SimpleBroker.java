package de.local.simulate.address.jms.broker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
//import javax.jms.*;

import de.local.simulate.address.jms.common.*;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.directory.api.ldap.model.cursor.Tuple;


public class SimpleBroker {
    private final HashMap<String, Topic> topics = new HashMap<>();
    private final HashMap<String, Stock> stocks;
    private final HashMap<String, Tuple<Queue, Queue>> userQueues = new HashMap<>(); //inputQueue, outputQueue for each user
    private final jakarta.jms.Connection connection;
    private final Session session;

    public SimpleBroker(List<Stock> stockList) throws jakarta.jms.JMSException {
        String brokerAddress = "tcp://127.0.0.1:61616";
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerAddress);
        connectionFactory.setTrustAllPackages(true);
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        createRegistrationQueue();
        stocks = new HashMap<>();
        System.out.println("INITIAL STOCKS:");
        for (Stock stock : stockList) {
            System.out.println(stock);
            stocks.put(stock.getName(), stock);
            Topic topic = session.createTopic(stock.getName());
            topics.put(stock.getName(), topic);
        }
    }

    private void createRegistrationQueue() throws JMSException {
        Queue registrationQueue = session.createQueue("registrationQueue");
        MessageConsumer registrationConsumer = session.createConsumer(registrationQueue);
        MessageListener registerListener = new RegistrationListener();
        registrationConsumer.setMessageListener(registerListener);
    }

    public void stop() throws JMSException {
        session.close();
        connection.close();
    }
    
    public synchronized int buy(String stockName, int amount, double money) throws JMSException {
        if (!stocks.containsKey(stockName)) {
            System.out.println("Stock not registered");
            return -1;
        }
        Stock stock = stocks.get(stockName);
        if (stock.getAvailableCount() < amount) {
            System.out.println("Stock amount not available. Maximum amount: " + amount);
            return -1;
        }
        if(stock.getPrice() * amount > money){
            System.out.println("Client has not enough money.");
            return -1;
        }
        stock.setAvailableCount(stock.getAvailableCount() - amount);
        TextMessage updateMessage = session.createTextMessage("Stock update after BUY: " + stockName + " now with " + stock.getAvailableCount() + " available stocks.");
        MessageProducer producer = session.createProducer(topics.get(stockName));
        producer.send(updateMessage);
        return amount;
    }
    
    public synchronized int sell(String stockName, int amount) throws JMSException {
        if (!stocks.containsKey(stockName)) {
            System.out.println("Stock not registered");
            return -1; }
        Stock stock = stocks.get(stockName);
        if (stock.getAvailableCount() + amount > stock.getStockCount()) {
            System.out.println("Stock amount not available. Maximum amount: " + amount);
            return -1;
        }
        stock.setAvailableCount(stock.getAvailableCount() + amount);
        TextMessage updateMessage = session.createTextMessage("Stock update after SELL: " + stockName + " now with " + stock.getAvailableCount() + " available stocks.");
        MessageProducer producer = session.createProducer(topics.get(stockName));
        producer.send(updateMessage);
        return amount;
    }
    
    public synchronized ArrayList<Stock> getStockList() {
        return new ArrayList<>(stocks.values());
    }

    private class RegistrationListener implements MessageListener {
        @Override
        public void onMessage(Message message) {
            if (message instanceof ObjectMessage) {
                try {
                    Object body = ((ObjectMessage) message).getObject();
                    if (body instanceof BrokerMessage brokerMessage) {
                        if (brokerMessage.getType() == BrokerMessage.Type.SYSTEM_REGISTER) {
                            handleRegister((RegisterMessage) body);
                        } else if (brokerMessage.getType() == BrokerMessage.Type.SYSTEM_UNREGISTER) {
                            RegisterMessage registerMsg = (RegisterMessage) body;
                            String clientName = registerMsg.getClientName();
                            userQueues.remove(clientName);
                        }
                    }
                } catch (JMSException e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        private void handleRegister(RegisterMessage registerMessage) throws JMSException {
            String clientName = registerMessage.getClientName();
            if (userQueues.containsKey(clientName)) {
                return;
            }
            Queue outputQueue = session.createQueue(clientName + ".input");
            Queue inputQueue = session.createQueue(clientName + ".output");
            Tuple<Queue, Queue> tuple = new Tuple<>(outputQueue, inputQueue); //reversed to suit the scheme
            userQueues.put(clientName, tuple);
            MessageConsumer userConsumer = session.createConsumer(outputQueue);
            userConsumer.setMessageListener(new ClientCommandListener(clientName));
        }
    }

    private class ClientCommandListener implements MessageListener {
        private final String clientName;

        public ClientCommandListener(String clientName) {
            this.clientName = clientName;
        }

        @Override
        public void onMessage(Message message) {
            if (message instanceof ObjectMessage) {
                try {
                    Object body = ((ObjectMessage) message).getObject();
                    if (body instanceof BrokerMessage brokerMessage) {
                        switch (brokerMessage.getType()) {
                            case STOCK_BUY -> {
                                BuyMessage buyMsg = (BuyMessage) brokerMessage;
                                int result = buy(buyMsg.getStockName(), buyMsg.getAmount(), buyMsg.getMoney());
                                Stock stock = stocks.get(buyMsg.getStockName());
                                ResponseMessage responseMessage = new ResponseMessage(result, stock.getPrice(), "buy", stock.getName());
                                Queue inputQueue = userQueues.get(clientName).getValue();
                                MessageProducer producer = session.createProducer(inputQueue);
                                ObjectMessage objectMessage = session.createObjectMessage(responseMessage);
                                producer.send(objectMessage);
                                if (result != -1 ) {
                                    System.out.println("Bought: " + buyMsg.getStockName() + " " + buyMsg.getAmount());
                                }
                            }
                            case STOCK_SELL -> {
                                SellMessage sellMsg = (SellMessage) brokerMessage;
                                int result = sell(sellMsg.getStockName(), sellMsg.getAmount());
                                Stock stock = stocks.get(sellMsg.getStockName());
                                ResponseMessage responseMessage = new ResponseMessage(result, stock.getPrice(), "sell", stock.getName());
                                Queue outputQueue = userQueues.get(clientName).getValue();
                                MessageProducer producer = session.createProducer(outputQueue);
                                ObjectMessage objectMessage = session.createObjectMessage(responseMessage);
                                producer.send(objectMessage);
                                if (result != -1) {
                                    System.out.println("Sold: " + sellMsg.getStockName() + " " + sellMsg.getAmount());
                                }
                            }
                            case STOCK_LIST -> {
                                MessageProducer messageProducer = session.createProducer(userQueues.get(clientName).getValue());
                                ArrayList<Stock> stocks =  getStockList();
                                ListMessage listMessage = new ListMessage(stocks);
                                ObjectMessage objectMessage = session.createObjectMessage(listMessage);
                                messageProducer.send(objectMessage);
                                System.out.println("List: " + stocks.toString());
                            }
                        }
                    }
                } catch (JMSException e) {
                    System.err.println(e.toString());
                }
            }
        }
    }
}
