package com.example.helloworld.network.protocol;

public class SessionSelectMessage {
    public String type = MessageType.TYPE_SESSION_SELECT;
    public String client_id;
    public String session_id;

    public SessionSelectMessage(String clientId, String sessionId) {
        this.client_id = clientId;
        this.session_id = sessionId;
    }
}
