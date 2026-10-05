package com.yurii.zhuravlov.notificationservice.ws.frame;

public record ServerFrame(String type, Long userId, String message, Object event) {

    public static ServerFrame ready(Long userId) {
        return new ServerFrame("READY", userId, null, null);
    }

    public static ServerFrame pong() {
        return new ServerFrame("PONG", null, null, null);
    }

    public static ServerFrame error(String message) {
        return new ServerFrame("ERROR", null, message, null);
    }

    public static ServerFrame event(Object event) {
        return new ServerFrame("EVENT", null, null, event);
    }
}