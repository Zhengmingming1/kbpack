package com.kbpack.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int code,
        String message,
        @JsonProperty("trace_id") String traceId
) {
}
