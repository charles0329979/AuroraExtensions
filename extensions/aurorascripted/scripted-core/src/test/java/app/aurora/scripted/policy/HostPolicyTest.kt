package app.aurora.scripted.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPolicyTest {
    @Test
    fun acceptsSameHost() {
        assertTrue(HostPolicy.isAllowed("https://example.com/base", "https://example.com/a.png"))
    }

    @Test
    fun rejectsOtherHost() {
        assertFalse(HostPolicy.isAllowed("https://example.com", "https://evil.com/a.png"))
    }

    @Test
    fun acceptsAllowedHosts() {
        assertTrue(
            HostPolicy.isAllowed(
                "https://example.com",
                "https://placehold.co/1.png",
                setOf("placehold.co"),
            ),
        )
    }

    @Test
    fun rejectsFtp() {
        assertFalse(HostPolicy.isAllowed("https://example.com", "ftp://example.com/a"))
    }
}
