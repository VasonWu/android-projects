package com.pipixia.network.protocol;

public class InputMessage {
    public String type = MessageType.TYPE_INPUT;
    public String client_id;
    public String text;
    public String session_id;

    public InputMessage(String text, String clientId, String sessionId) {
        this.client_id = clientId;
        this.text = text;
        this.session_id = sessionId;
    }
}
