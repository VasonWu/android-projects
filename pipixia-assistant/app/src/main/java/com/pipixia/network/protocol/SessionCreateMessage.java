package com.pipixia.network.protocol;

public class SessionCreateMessage {
    public String type = MessageType.TYPE_SESSION_CREATE;
    public String client_id;
    public String name;

    public SessionCreateMessage(String clientId) {
        this.client_id = clientId;
    }

    public SessionCreateMessage(String clientId, String name) {
        this.client_id = clientId;
        this.name = name;
    }
}
