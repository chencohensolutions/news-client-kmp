package news.app.server

import news.app.server.discovery.DhtRecord
import news.app.server.discovery.ServerAddresses
import news.app.server.discovery.ServerRecord
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A news server's DHT record as news-server-go's own `discovery` code seals it (made with its seal and open on
 * Nov 11, 2026, at b307ad1), read back by the app. All hex.
 */
private object NewsDhtFixture {
    /** The ed25519 public key of the seed 00 01 … 1f. */
    const val KEY = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"

    /** The server's fingerprint: the seal's key is made from it. */
    const val FINGERPRINT = "903ed60d03a8101e46d164881f9b181197c1b8b2da9ae7755935377f3c0143f7"

    /** SHA-1(KEY bytes + "news-server/v1"). */
    const val TARGET = "12c866971ccaf3aa1de1be97b0ce695e3a3e846a"

    const val SEALED = "85f1488c3962d3c55b28ba6cc423da580c78b5d5280fcdb4857c430d601b013ca0a681d4c2d9801525bbff8445cc19689dbc0e059b72b17538e98d97cba26aa0b2b034896fa28f575d4bc1be8935a9303d74af149f2ef2cd465f7b2d3e8f24d63b524a568ce4548c1e0514c99572b20b15be8c9e4cc0625fc25ccd9952d01611e8eb54f8de1454c0a79f14be58"

    val ADDRESSES = listOf("https://46.117.218.92:56400", "https://192.168.50.150:56399")
    const val AT = "2026-11-11T00:00:00Z"
}

class DhtRecordTest {
    private fun hex(s: String) = s.decodeHex().toByteArray()

    @Test
    fun theTargetIsSha1OfTheKeyAndTheNewsSalt() {
        assertEquals("news-server/v1", DhtRecord.SALT)
        assertEquals(NewsDhtFixture.TARGET, DhtRecord.target(NewsDhtFixture.KEY).toByteString().hex())
    }

    @Test
    fun opensTheNewsServersSeal() {
        val record = assertNotNull(DhtRecord.open(hex(NewsDhtFixture.SEALED), NewsDhtFixture.FINGERPRINT))
        assertEquals(NewsDhtFixture.ADDRESSES, record.addresses)
        assertEquals(Instant.parse(NewsDhtFixture.AT).toEpochMilliseconds(), record.at)
    }

    @Test
    fun anotherFingerprintOrAChangedByteDoesNotOpen() {
        assertNull(DhtRecord.open(hex(NewsDhtFixture.SEALED), "00" + NewsDhtFixture.FINGERPRINT.drop(2)))
        val changed = hex(NewsDhtFixture.SEALED).also { it[40] = (it[40].toInt() xor 0x10).toByte() }
        assertNull(DhtRecord.open(changed, NewsDhtFixture.FINGERPRINT))
        assertNull(DhtRecord.open(ByteArray(10), NewsDhtFixture.FINGERPRINT))
    }

    @Test
    fun aRecordSealedByTheAppOpensAgain() {
        val record = ServerRecord(NewsDhtFixture.ADDRESSES, Instant.parse(NewsDhtFixture.AT).toEpochMilliseconds())
        val sealed = DhtRecord.seal(record, NewsDhtFixture.FINGERPRINT, ByteArray(24) { it.toByte() })
        assertEquals(record, DhtRecord.open(sealed, NewsDhtFixture.FINGERPRINT))
    }

    @Test
    fun theWorkingAddressGoesFirstThenTheServersThenTheOldOnes() {
        val enrolled = "https://192.168.50.150:56399"
        val kept = listOf(enrolled)
        val merged = ServerAddresses.merge(kept, "https://203.0.113.7:56400", listOf("203.0.113.7:56399", "https://[2001:db8::7]:56399"), enrolled)
        assertEquals(listOf("https://203.0.113.7:56400", "https://203.0.113.7:56399", "https://[2001:db8::7]:56399", enrolled), merged)
        assertEquals(listOf(enrolled) + (merged - enrolled), ServerAddresses.working(merged, enrolled, enrolled))
        // At most 16, the enrolled one kept.
        val many = (1..30).map { "https://10.0.0.$it:1" }
        val capped = ServerAddresses.merge(listOf(enrolled), "https://10.0.1.1:1", many, enrolled)
        assertEquals(ServerAddresses.MAX, capped.size)
        assertEquals("https://10.0.1.1:1", capped.first())
        assertEquals(enrolled, capped.last())
    }
}
