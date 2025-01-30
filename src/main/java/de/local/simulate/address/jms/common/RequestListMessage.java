package de.local.simulate.address.jms.common;


public class RequestListMessage extends BrokerMessage {
    public RequestListMessage() {
        super(Type.STOCK_LIST);
    }
}
