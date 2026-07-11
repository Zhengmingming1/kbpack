package com.kbpack.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreviewTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:00:00Z");
    private static final String PACKAGE_ID = "pkg_018f6f3a-2e4b-7c3d-9a1b-4e5f6a7b8c9d";
    private static final String VERSION_ID = "ver_018f6f3a-2e4b-7c3d-9a1b-4e5f6a7b8c9e";

    @Test
    void issuesAndValidatesTicketAndSession() {
        PreviewTicketService service = serviceAt(NOW);

        String ticket = service.issueTicket(PACKAGE_ID, VERSION_ID);
        String session = service.issueSession(PACKAGE_ID, VERSION_ID);

        assertDoesNotThrow(() -> service.validateTicket(ticket, PACKAGE_ID, VERSION_ID));
        assertDoesNotThrow(() -> service.validateSession(session, PACKAGE_ID, VERSION_ID));
    }

    @Test
    void rejectsTamperedTicket() {
        PreviewTicketService service = serviceAt(NOW);
        String ticket = service.issueTicket(PACKAGE_ID, VERSION_ID);
        char replacement = ticket.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = replacement + ticket.substring(1);

        assertInvalid(() -> service.validateTicket(tampered, PACKAGE_ID, VERSION_ID));
    }

    @Test
    void rejectsTicketAfterExpiryAndClockTolerance() {
        String ticket = serviceAt(NOW).issueTicket(PACKAGE_ID, VERSION_ID);
        PreviewTicketService later = serviceAt(NOW.plusSeconds(66));

        assertInvalid(() -> later.validateTicket(ticket, PACKAGE_ID, VERSION_ID));
    }

    @Test
    void rejectsNonceReplay() {
        PreviewTicketService service = serviceAt(NOW);
        String ticket = service.issueTicket(PACKAGE_ID, VERSION_ID);

        service.validateTicket(ticket, PACKAGE_ID, VERSION_ID);

        assertInvalid(() -> service.validateTicket(ticket, PACKAGE_ID, VERSION_ID));
    }

    @Test
    void rejectsTicketForDifferentPackageOrVersion() {
        PreviewTicketService service = serviceAt(NOW);
        String ticket = service.issueTicket(PACKAGE_ID, VERSION_ID);

        assertInvalid(() -> service.validateTicket(ticket, "pkg_other", VERSION_ID));
        assertInvalid(() -> service.validateTicket(ticket, PACKAGE_ID, "ver_other"));
    }

    private static PreviewTicketService serviceAt(Instant instant) {
        KbpackProperties properties = new KbpackProperties();
        properties.getPreview().setTicketSecret("unit-test-preview-secret-with-32-bytes");
        properties.getPreview().setTicketTtlSeconds(60);
        properties.getPreview().setSessionTtlSeconds(1_800);
        return new PreviewTicketService(
                properties,
                new ObjectMapper(),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private static void assertInvalid(Executable executable) {
        ApiException exception = assertThrows(ApiException.class, executable);
        assertEquals(ErrorCode.PREVIEW_CREDENTIAL_INVALID, exception.getErrorCode());
    }
}
