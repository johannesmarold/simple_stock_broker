package de.local.simulate.address.jms.common;

public class ResponseMessage extends BrokerMessage {
    private int result;
    private String action;

    private double price;

    private String stockName;
    public ResponseMessage(int result, double price, String type, String stockName) {
        super(Type.RESPONSE_STATUS);
        this.result = result;
        this.price = price;
        this.action = type; // "buy" or "sell"
        this.stockName = stockName;
    }

    public int getResult() {
        return result;
    }

    public String getAction() { return action; }

    public double getPrice() { return price; }

    public String getStockName() { return stockName; }
}
