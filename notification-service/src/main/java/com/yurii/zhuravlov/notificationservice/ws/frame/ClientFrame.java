package com.yurii.zhuravlov.notificationservice.ws.frame;

/** Everything the client can send. Only AUTH carries a token; PING is for manual testing. */
public record ClientFrame(String type, String token) {

    public static final String AUTH = "AUTH";
    public static final String PING = "PING";
}