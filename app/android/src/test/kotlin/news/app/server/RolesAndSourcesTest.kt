package news.app.server

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import news.app.adminError
import news.app.deviceLine
import news.app.feedHidden
import news.app.qrPixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RolesAndSourcesTest {
    private fun source(id: String, enabled: Boolean = true, hidden: Boolean = false) =
        ServerSource(id, id, "https://$id.example", "he", enabled = enabled, hidden = hidden)

    @Test
    fun theFeedLeavesOutHiddenAndUnreadSites() {
        val server = listOf(source("alpha"), source("beta", hidden = true), source("gamma", enabled = false))
        assertEquals(setOf("beta", "gamma"), feedHidden(emptySet(), server))
    }

    @Test
    fun aSiteShownAgainOnAnotherDeviceComesBack() {
        // The phone had alpha hidden; the server says this user shows it now: the server's choice counts.
        assertEquals(emptySet(), feedHidden(setOf("alpha"), listOf(source("alpha"))))
    }

    @Test
    fun aSiteTheServerDoesntKnowStaysAsThePhoneHadIt() {
        assertEquals(setOf("delta"), feedHidden(setOf("delta"), listOf(source("alpha"))))
    }

    @Test
    fun adminErrorsSayWhatHappened() {
        assertEquals("רק מנהל יכול לעשות את זה", adminError(ServerException("admins only", "FORBIDDEN")))
        assertEquals("the server's last admin stays", adminError(ServerException("the server's last admin stays", "BAD_REQUEST")))
        assertEquals("השרת לא עונה", adminError(java.io.IOException("down")))
    }

    @Test
    fun aRevokedDeviceSaysWhen() {
        val device = ServerDevice(
            "01", "Pixel", "STRONGBOX", Instant.parse("2026-01-02T10:00:00Z"), Instant.parse("2027-01-02T10:00:00Z"),
            revokedAt = Instant.parse("2026-05-06T10:00:00Z"),
        )
        assertTrue("בוטל" in deviceLine(device))
        assertTrue("בתוקף" in deviceLine(device.copy(revokedAt = null)))
    }

    @Test
    fun theInvitesQrCodeReadsBackAsItsLink() {
        val link = "news://enroll?code=K7QM3XRP&server=192.168.50.2:56399&fingerprint=" + "ab".repeat(32)
        val size = 400
        val pixels = qrPixels(link, size)
        val read = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
        assertEquals(link, read.text)
    }
}
