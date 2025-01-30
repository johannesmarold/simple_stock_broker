package de.local.simulate.address.jms.common;


public class BuyMessage extends BrokerMessage {
    private String stockName;
    private int amount;
    private double money;
    
    public BuyMessage(String stockName, int amount, double money) {
        super(Type.STOCK_BUY);
        this.stockName = stockName;
        this.amount = amount;
        this.money = money;
    }

    public String getStockName() {
        return stockName;
    }

    public int getAmount() {
        return amount;
    }
    public double getMoney(){
        return money;
    }
}
