package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionGateTest {
    @Test fun gatedChoiceFollowUpKeepsQuestionContentAndGate() {
        val catalogue = Catalogue.parse(
            """
            {
              "version":"test",
              "reviewed":false,
              "groups":[],
              "fields":{
                "hasIssue":{"label":"Issue","type":"yesno","choices":[],"danger":false},
                "issueType":{"label":"Issue type","type":"choice","choices":["type one","type two"],"danger":false}
              },
              "questions":{
                "q_issueType":{
                  "field":"issueType",
                  "ask":"Which kind did you notice?",
                  "type":"choice",
                  "priority":10,
                  "gate":{"field":"hasIssue","any":[true]},
                  "help":"This asks which kind you noticed.\nThe kind helps your doctor understand it."
                }
              },
              "problems":[{
                "id":"synthetic",
                "label":"Synthetic concern",
                "group":"test",
                "dept":"test",
                "region":"whole",
                "glyph":"test",
                "syn":[],
                "fields":["hasIssue","issueType"],
                "fu":["q_issueType"],
                "red":false
              }]
            }
            """.trimIndent()
        )
        val problem = catalogue.problem("synthetic")!!
        val asks = Interview.extended(catalogue, problem, emptyMap())
        val issueTypeAsks = asks.filter { it.field == "issueType" }

        assertEquals(1, issueTypeAsks.size)
        val ask = issueTypeAsks.single()
        assertEquals("q_issueType", ask.id)
        assertEquals("Which kind did you notice?", ask.text)
        assertEquals("This asks which kind you noticed.\nThe kind helps your doctor understand it.", ask.help)
        assertEquals(Interview.Kind.CHOICE, ask.kind)
        assertEquals(listOf("type one", "type two"), ask.choices.map { it.value })
        assertEquals(listOf("Type one", "Type two"), ask.choices.map { it.label })

        assertEquals("hasIssue", ask.gate?.field)
        assertFalse(Interview.applies(ask, mapOf("hasIssue" to Fact(false, Source.ASKED))))
        assertTrue(Interview.applies(ask, mapOf("hasIssue" to Fact(true, Source.ASKED))))
    }
}
