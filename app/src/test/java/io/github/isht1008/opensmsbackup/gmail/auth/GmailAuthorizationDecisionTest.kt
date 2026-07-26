package io.github.isht1008.opensmsbackup.gmail.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class GmailAuthorizationDecisionTest {
    @Test fun `successful task with resolution is not treated as authorized`() {
        assertEquals(
            GmailAuthorizationDecision.USER_RESOLUTION_REQUIRED,
            authorizationDecision(true, emptyList())
        )
    }

    @Test fun `only completed requested Gmail scope is authorized`() {
        assertEquals(
            GmailAuthorizationDecision.AUTHORIZED,
            authorizationDecision(false, listOf(GmailAuthorizationManager.GMAIL_SCOPE))
        )
        assertEquals(GmailAuthorizationDecision.DENIED, authorizationDecision(false, emptyList()))
    }
}
