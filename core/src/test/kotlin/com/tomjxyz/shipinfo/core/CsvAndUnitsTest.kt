package com.tomjxyz.shipinfo.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class CsvAndUnitsTest {
    @Test
    fun escapesSpecialCharacters() {
        val sw = StringWriter()
        CsvWriter(sw).row("plain", "a,b", "say \"hi\"", null, 1.5, 2.0)
        assertEquals("plain,\"a,b\",\"say \"\"hi\"\"\",,1.5,2\r\n", sw.toString())
    }

    @Test
    fun isoTimestamp() {
        assertEquals("1970-01-01T00:00:01.500Z", CsvWriter.isoUtc(1500))
    }

    @Test
    fun headerDependsOnChannels() {
        assertEquals(
            listOf("timestamp_utc", "elapsed_s", "speed_kn", "roll_deg", "pitch_deg", "lateral_g", "vertical_g"),
            SampleColumns.header(setOf(Channel.SPEED, Channel.ROLL)),
        )
        val row = SampleColumns.row(
            setOf(Channel.SPEED, Channel.ROLL), 2000, 1000,
            1.0, 2.0, 3.0, 12.5, 90.0, 91.0, 3.0, 1.0, 0.1, 0.0,
        )
        assertEquals(7, row.size)
        assertEquals(1.0, row[1])
        assertEquals(12.5, row[2])
    }

    @Test
    fun channelEncodingRoundTrips() {
        val set = setOf(Channel.HEADING, Channel.GPS)
        assertEquals("GPS,HEADING", SampleColumns.encode(set))
        assertEquals(set, SampleColumns.decode("GPS,HEADING"))
        assertEquals(emptySet<Channel>(), SampleColumns.decode(""))
    }

    @Test
    fun unitConversions() {
        assertEquals(19.43844, Units.msToKnots(10.0), 1e-4)
        assertEquals(350.0, Units.normalizeBearing(-10.0), 1e-9)
        assertEquals(10.0, Units.normalizeBearing(370.0), 1e-9)
        // One minute of latitude is one nautical mile.
        assertEquals(1.0, Units.distanceNm(0.0, 0.0, 1.0 / 60.0, 0.0), 1e-3)
        assertEquals("54°30.000'N", Units.formatLat(54.5))
        assertEquals("003°15.000'W", Units.formatLon(-3.25))
        assertEquals("NE", Units.cardinal(47.0))
        assertEquals("N", Units.cardinal(355.0))
    }
}
