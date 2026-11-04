package ru.ssau.rbs.exception;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final HttpHeaders headers = new HttpHeaders();
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException unauthenticated(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", detail);
    }

    public static ApiException emailTaken(String email) {
        return new ApiException(HttpStatus.CONFLICT, "email_taken", "Email уже зарегистрирован: " + email);
    }

    public static ApiException rateLimited(Duration retryAfter) {
        ApiException exception = new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                "Слишком много неудачных попыток входа, попробуйте позже");
        exception.headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
        return exception;
    }

    public ApiException withProperty(String name, Object value) {
        this.properties.put(name, value);
        return this;
    }
}
