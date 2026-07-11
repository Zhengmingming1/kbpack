package com.kbpack.common.error;

import org.springframework.http.HttpStatus;

/**
 * Business error codes (see docs/00-canonical-spec.md §6).
 * Ranges: 1000 general, 2000 package/upload, 3000 parse, 4000 search, 5000 preview, 6000 user.
 */
public enum ErrorCode {
    BAD_REQUEST(1000, HttpStatus.BAD_REQUEST, "请求参数错误"),
    INVALID_CREDENTIALS(1001, HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    USER_LOCKED(1002, HttpStatus.LOCKED, "账号已锁定，请稍后再试"),
    TAG_NAME_DUPLICATE(1003, HttpStatus.CONFLICT, "标签名重复"),
    UNAUTHORIZED(1101, HttpStatus.UNAUTHORIZED, "未登录或会话已过期"),
    FORBIDDEN(1102, HttpStatus.FORBIDDEN, "无权限执行该操作"),
    NOT_FOUND(1103, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(1104, HttpStatus.CONFLICT, "状态冲突"),
    INTERNAL_ERROR(1500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    UPLOAD_LIMIT_EXCEEDED(2002, HttpStatus.PAYLOAD_TOO_LARGE, "上传内容超过限额"),
    CONTENT_UNCHANGED(2003, HttpStatus.CONFLICT, "该内容与当前版本完全相同"),
    ARCHIVE_UNSAFE(2004, HttpStatus.UNPROCESSABLE_ENTITY, "上传包安全校验失败"),
    PACKAGE_NOT_FOUND(2005, HttpStatus.NOT_FOUND, "知识包不存在"),
    VERSION_NOT_FOUND(2006, HttpStatus.NOT_FOUND, "版本不存在"),
    CURRENT_VERSION_DELETE_FORBIDDEN(2007, HttpStatus.CONFLICT, "不能删除当前版本，请先切换当前版本"),

    PARSE_FAILED(3000, HttpStatus.UNPROCESSABLE_ENTITY, "解析失败"),
    TASK_MAX_RETRIES(3001, HttpStatus.CONFLICT, "任务已超过最大重试次数"),
    SEARCH_UNAVAILABLE(4000, HttpStatus.SERVICE_UNAVAILABLE, "搜索服务不可用"),
    PREVIEW_TICKET_INVALID(5000, HttpStatus.UNAUTHORIZED, "预览票据无效或已过期"),
    PREVIEW_FORBIDDEN(5001, HttpStatus.FORBIDDEN, "无权限访问该知识包的预览"),
    PREVIEW_NOT_READY(5002, HttpStatus.NOT_FOUND, "版本不存在或尚未解析成功"),
    PREVIEW_CREDENTIAL_INVALID(5003, HttpStatus.UNAUTHORIZED, "预览凭证无效或已过期");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
