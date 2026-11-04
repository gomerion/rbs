package ru.ssau.rbs.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import ru.ssau.rbs.security.RequestIdFilter;

public final class ProblemWriter {

    private ProblemWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body(status, code, detail));
    }

    private static String body(HttpStatus status, String code, String detail) {
        return "{\"type\":\"about:blank\""
                + ",\"title\":\"" + escape(status.getReasonPhrase()) + "\""
                + ",\"status\":" + status.value()
                + ",\"code\":\"" + escape(code) + "\""
                + ",\"detail\":\"" + escape(detail) + "\""
                + ",\"request_id\":\"" + escape(RequestIdFilter.currentRequestId()) + "\"}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
