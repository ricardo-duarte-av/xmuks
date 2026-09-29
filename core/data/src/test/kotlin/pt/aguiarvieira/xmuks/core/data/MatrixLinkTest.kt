package pt.aguiarvieira.xmuks.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.commands.MatrixLink
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver

class MatrixLinkTest {
    @Test
    fun `every form of matrix URI`() {
        assertEquals(MatrixLink("#matrix:matrix.org", null, emptyList()), MatrixLink.parse("matrix:r/matrix:matrix.org"))
        assertEquals("!abcdefghj:matrix.org", MatrixLink.parse("matrix:roomid/abcdefghj:matrix.org")?.id)
        assertEquals("@alice:matrix.org", MatrixLink.parse("matrix:u/alice:matrix.org")?.id)
        val event = MatrixLink.parse("matrix:roomid/abcdefg1234:matrix.org/e/event123456789?via=matrix.org&via=x.org")!!
        assertEquals("!abcdefg1234:matrix.org", event.id)
        assertEquals("\$event123456789", event.eventId)
        assertEquals(listOf("matrix.org", "x.org"), event.via)
        assertEquals("\$event123456789", MatrixLink.parse("matrix:r/matrix:matrix.org/e/event123456789")?.eventId)
        assertEquals("#matrix:matrix.org", MatrixLink.parse("https://matrix.to/#/%23matrix%3Amatrix.org")?.id)
        assertNull(MatrixLink.parse("https://example.org"))
    }

    @Test
    fun `our room links read back as they were made`() {
        val link = MatrixLink.parse(LinkResolver.roomUri("!r/oom:s.org", "\$ev+nt"))!!
        assertEquals("!r/oom:s.org", link.id)
        assertEquals("\$ev+nt", link.eventId)
    }
}
