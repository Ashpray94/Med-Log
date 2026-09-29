package com.suryaprakash.medlog

import com.suryaprakash.medlog.integration.LinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPolicyTest {
    @Test fun outsideLinksOnlyOpenThePage() {
        assertEquals(LinkPolicy.PAGE_EMERGENCY, LinkPolicy.linkAction("call", false, false))
        assertEquals(LinkPolicy.PAGE_EMERGENCY, LinkPolicy.linkAction("sos", false, false))
        assertEquals(LinkPolicy.PAGE_HELP, LinkPolicy.linkAction("help", true, false))
        assertEquals(LinkPolicy.PAGE_HELP, LinkPolicy.linkAction("help", false, false))
    }

    @Test fun ownLinksStillAct() {
        assertEquals(LinkPolicy.CALL, LinkPolicy.linkAction("call", false, true))
        assertEquals(LinkPolicy.SOS, LinkPolicy.linkAction("sos", false, true))
        assertEquals(LinkPolicy.SEND, LinkPolicy.linkAction("help", true, true))
        assertEquals(LinkPolicy.PAGE_HELP, LinkPolicy.linkAction("help", false, true))
    }

    @Test fun otherPagesAreUnchanged() {
        for (h in listOf("tell", "meds", "doctor", "emergency")) for (t in listOf(true, false))
            assertEquals(LinkPolicy.NORMAL, LinkPolicy.linkAction(h, true, t))
    }
}
