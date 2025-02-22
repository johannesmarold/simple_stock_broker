package de.local.simulate.address.jms.common;


public class UnregisterMessage extends BrokerMessage {
    private String clientName;
    
    public UnregisterMessage(String clientName) {
        super(Type.SYSTEM_UNREGISTER);
        
        this.clientName = clientName;
    }
    
    public String getClientName() {
        return clientName;
    }
}
