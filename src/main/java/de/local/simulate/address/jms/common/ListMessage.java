package de.local.simulate.address.jms.common;

import java.util.List;


public class ListMessage extends BrokerMessage {
    private List<Stock> stocks;
    
    public ListMessage(List<Stock> stocks) {
        super(Type.STOCK_LIST);
        this.stocks = stocks;
    }
    
    public List<Stock> getStocks() {
        return stocks;
    }
}
