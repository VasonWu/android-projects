package com.pipixia.network.protocol;

public class SessionListMessage {
    public String type = MessageType.TYPE_SESSION_LIST;
    public String client_id;

    public SessionListMessage(String clientId) {
        this.client_id = clientId;
    }
}
