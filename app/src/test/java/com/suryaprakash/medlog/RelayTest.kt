package com.suryaprakash.medlog

import com.suryaprakash.medlog.help.Relay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test fun mailboxNamesComeFromTheKey() {
        val down = Relay.topic(key, Relay.DOWN)
        // both phones work out the same name on their own
        assertEquals(down, Relay.topic(key.copyOf(), Relay.DOWN))
        assertTrue(down.matches(Regex("medlog-[0-9a-f]{32}")))
        // each direction and each pairing has its own mailbox
        assertNotEquals(down, Relay.topic(key, Relay.UP))
        assertNotEquals(down, Relay.topic(ByteArray(32) { 7 }, Relay.DOWN))
    }

    @Test fun readsMessagesAndSkipsKeepAlives() {
        assertNull(Relay.parse("""{"id":"a1","time":1,"event":"open","topic":"t"}"""))
        assertNull(Relay.parse("""{"id":"a2","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(Relay.parse("not json"))
        val m = Relay.parse("""{"id":"a3","time":1,"event":"message","topic":"t","message":"c2VhbGVk"}""")!!
        assertEquals("a3", m.id)
        assertEquals("t", m.topic)
        assertEquals("c2VhbGVk", m.text)
        assertNull(m.attachment)
    }

    @Test fun bigNotesArriveAsAttachments() {
        val m = Relay.parse("""{"id":"a4","event":"message","topic":"t","message":"You received a file: attachment.txt","attachment":{"name":"attachment.txt","url":"https://ntfy.sh/file/a4.txt"}}""")!!
        assertNull(m.text)
        assertEquals("https://ntfy.sh/file/a4.txt", m.attachment)
    }
}
