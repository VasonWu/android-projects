package com.example.helloworld.network.protocol;

public class InputMessage {
    public String type = MessageType.TYPE_INPUT;
    public String text;

    public InputMessage(String text) {
        this.text = text;
    }
}
