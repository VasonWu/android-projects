package com.example.helloworld.network.protocol;

public class CancelMessage {
    public String type = MessageType.TYPE_CANCEL;
    public String client_id;

    public CancelMessage(String clientId) {
        this.client_id = clientId;
    }
}
