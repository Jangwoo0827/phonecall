package com.example.superdialer.dialer

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickDialsTest {
    @Test fun roundTrip() {
        val map = mapOf('2' to QuickDial("A", "010-1111-2222"), '9' to QuickDial("B", "02-123-4567"))
        assertEquals(map, QuickDials.decode(QuickDials.encode(map)))
    }

    @Test fun badInputIsIgnored() {
        assertEquals(emptyMap<Char, QuickDial>(), QuickDials.decode(null))
        assertEquals(emptyMap<Char, QuickDial>(), QuickDials.decode("not json"))
        val parsed = QuickDials.decode("""{"0":{"name":"x","number":"1"},"22":{"name":"y","number":"2"},"3":{"name":"z","number":""},"4":{"name":"ok","number":"5"}}""")
        assertEquals(mapOf('4' to QuickDial("ok", "5")), parsed)
    }
}
