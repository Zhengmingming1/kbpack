package com.kbpack.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeContractTest {

    @Test
    void matchesPublishedApiErrorCodes() {
        assertError(ErrorCode.INVALID_CREDENTIALS, 1001, HttpStatus.UNAUTHORIZED);
        assertError(ErrorCode.USER_LOCKED, 1002, HttpStatus.LOCKED);
        assertError(ErrorCode.TAG_NAME_DUPLICATE, 1003, HttpStatus.CONFLICT);
        assertError(ErrorCode.UPLOAD_LIMIT_EXCEEDED, 2002, HttpStatus.PAYLOAD_TOO_LARGE);
        assertError(ErrorCode.CONTENT_UNCHANGED, 2003, HttpStatus.CONFLICT);
        assertError(ErrorCode.ARCHIVE_UNSAFE, 2004, HttpStatus.UNPROCESSABLE_ENTITY);
        assertError(ErrorCode.PACKAGE_NOT_FOUND, 2005, HttpStatus.NOT_FOUND);
        assertError(ErrorCode.VERSION_NOT_FOUND, 2006, HttpStatus.NOT_FOUND);
        assertError(ErrorCode.CURRENT_VERSION_DELETE_FORBIDDEN, 2007, HttpStatus.CONFLICT);
        assertError(ErrorCode.TASK_MAX_RETRIES, 3001, HttpStatus.CONFLICT);
        assertError(ErrorCode.PREVIEW_FORBIDDEN, 5001, HttpStatus.FORBIDDEN);
        assertError(ErrorCode.PREVIEW_NOT_READY, 5002, HttpStatus.NOT_FOUND);
        assertError(ErrorCode.PREVIEW_CREDENTIAL_INVALID, 5003, HttpStatus.UNAUTHORIZED);
    }

    private void assertError(ErrorCode error, int code, HttpStatus status) {
        assertThat(error.getCode()).isEqualTo(code);
        assertThat(error.getHttpStatus()).isEqualTo(status);
    }
}
