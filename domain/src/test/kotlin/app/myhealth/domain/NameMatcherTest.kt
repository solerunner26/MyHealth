package app.myhealth.domain

import app.myhealth.domain.importing.NameMatch
import app.myhealth.domain.importing.NameMatcher
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameMatcherTest {
    private fun m(id: String, n: String) = FamilyMember(id, n, Relationship.Other)
    private val family = listOf(m("1", "Ravi Shah"), m("2", "Meera Ravi Shah"), m("3", "Aarav Shah"), m("4", "Kokila Patel"))

    @Test fun titlesCaseAndPunctuationIgnored() {
        assertEquals(NameMatch.Exact, NameMatcher.best("Mr. RAVI SHAH", family)!!.match)
        assertEquals("1", NameMatcher.best("Mr. RAVI SHAH", family)!!.member.id)
        assertEquals("4", NameMatcher.best("Smt. Kokila B. Patel", family)!!.member.id)
        assertEquals("Ravi Shah", NameMatcher.cleanName("MR. RAVI   SHAH."))
    }

    @Test fun likelyAndInitials() {
        assertEquals("3", NameMatcher.best("Master Aarav", family)!!.member.id)
        assertEquals("4", NameMatcher.best("K. Patel", family)!!.member.id)
    }

    @Test fun unknownAndAmbiguousNamesAskTheUser() {
        assertNull(NameMatcher.best("Suresh Mehta", family))
        assertNull(NameMatcher.best("Shah", listOf(m("1", "Ravi Shah"), m("3", "Aarav Shah"))))
        assertNull(NameMatcher.best(null, family))
        assertNull(NameMatcher.best("", family))
    }
}
