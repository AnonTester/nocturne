package app.nocturne.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbHostTest {
    @Test fun plainHostUsesDefaultPort() = assertEquals("nas.local" to 445, SmbShareProvider.parseHost("nas.local"))

    @Test fun hostWithPort() = assertEquals("nas.local" to 4455, SmbShareProvider.parseHost(" nas.local:4455 "))

    @Test fun invalidPortIsKeptInTheName() = assertEquals("nas:abc" to 445, SmbShareProvider.parseHost("nas:abc"))

    @Test fun outOfRangePortIsIgnored() = assertEquals("nas:99999" to 445, SmbShareProvider.parseHost("nas:99999"))

    @Test fun ipv6LiteralIsNotSplit() = assertEquals("fe80::1" to 445, SmbShareProvider.parseHost("fe80::1"))
}
