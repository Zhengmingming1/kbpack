package com.kbpack.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PreviewTicketService {
    private final KbpackProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    @Autowired
    public PreviewTicketService(KbpackProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    PreviewTicketService(KbpackProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String issueTicket(String packageId, String versionId) {
        long expiresAt = clock.instant().getEpochSecond() + properties.getPreview().getTicketTtlSeconds();
        return sign(new Credential(packageId, versionId, expiresAt, UUID.randomUUID().toString()));
    }

    public String issueSession(String packageId, String versionId) {
        long expiresAt = clock.instant().getEpochSecond() + properties.getPreview().getSessionTtlSeconds();
        return sign(new Credential(packageId, versionId, expiresAt, null));
    }

    public void validateTicket(String token, String packageId, String versionId) {
        Credential credential = verify(token, packageId, versionId);
        if (credential.nonce() == null || credential.nonce().isBlank()) invalid();
        long now = clock.instant().getEpochSecond();
        usedNonces.entrySet().removeIf(entry -> entry.getValue() < now);
        if (usedNonces.putIfAbsent(credential.nonce(), credential.exp()) != null) invalid();
    }

    public void validateSession(String token, String packageId, String versionId) {
        Credential credential = verify(token, packageId, versionId);
        if (credential.nonce() != null) invalid();
    }

    private String sign(Credential credential) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(credential));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
            return payload + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign preview credential", e);
        }
    }

    private Credential verify(String token, String packageId, String versionId) {
        if (token == null || token.isBlank()) return invalid();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) return invalid();
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            byte[] expected = hmac(parts[0]);
            if (!MessageDigest.isEqual(expected, actual)) return invalid();
            byte[] json = Base64.getUrlDecoder().decode(parts[0]);
            Credential credential = objectMapper.readValue(json, Credential.class);
            long now = clock.instant().getEpochSecond();
            if (!packageId.equals(credential.pkg()) || !versionId.equals(credential.ver())
                    || credential.exp() + 5 < now) return invalid();
            return credential;
        } catch (Exception e) {
            if (e instanceof ApiException api) throw api;
            return invalid();
        }
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getPreview().getTicketSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static <T> T invalid() {
        throw new ApiException(ErrorCode.PREVIEW_CREDENTIAL_INVALID);
    }

    public record Credential(String pkg, String ver, long exp, String nonce) {}
}
