package dev.learning.contracts.infrai;

import com.fasterxml.jackson.databind.JsonNode;

public class InfraiException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final JsonNode details;

    public InfraiException(String code, int httpStatus, JsonNode details) {
        super(details.path("message").asText(code));
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public JsonNode details() { return details; }
}
