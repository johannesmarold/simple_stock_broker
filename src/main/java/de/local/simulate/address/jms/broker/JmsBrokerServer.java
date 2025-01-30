package de.local.simulate.address.jms.broker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.local.simulate.address.jms.common.Stock;
import jakarta.jms.JMSException;

public class JmsBrokerServer {
    public static void main(String[] args) {
        try {
            List<Stock> stocks = new ArrayList<>();
            stocks.add(new Stock("ALDI", 200, 2.0));
            stocks.add(new Stock("LIDL", 300, 1.0));
            stocks.add(new Stock("NETTO", 1000, 0.5));
            stocks.add(new Stock("REWE", 658, 0.5));
            stocks.add(new Stock("NAHKAUF", 455, 0.5));
            stocks.add(new Stock("TESCO", 503, 0.5));
            stocks.add(new Stock("NORMA", 19, 0.06));
            stocks.add(new Stock("ICA", 670, 0.5));
            stocks.add(new Stock("WALMART", 347, 0.5));
            stocks.add(new Stock("KAUFLAND", 250, 0.5));

            SimpleBroker broker = new SimpleBroker( stocks); //default activeMQ socket
            System.in.read();
            broker.stop();
        } catch (JMSException | IOException ex) {
            Logger.getLogger(JmsBrokerServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
