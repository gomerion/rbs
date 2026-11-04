package ru.ssau.rbs.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientIp() {
    }

    public static InetAddress of(HttpServletRequest request) {
        String raw = rawAddress(request);
        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException ex) {
            return InetAddress.getLoopbackAddress();
        }
    }

    private static String rawAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
