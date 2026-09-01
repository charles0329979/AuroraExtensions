package app.aurora.scripted.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeValidationTest {
    @Test
    fun experimentalEnvelopeVersionIsDistinctFromHostV2() {
        assertEquals(3, SCRIPTED_PROTOCOL_VERSION)
    }

    @Test
    fun pagesOperationExists() {
        assertEquals("PAGES", ScriptedOperation.PAGES.name)
    }
}
