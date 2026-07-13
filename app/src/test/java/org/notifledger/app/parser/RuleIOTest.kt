package org.notifledger.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.notifledger.app.model.AmountExtractor
import org.notifledger.app.model.ParserRule
import org.notifledger.app.model.PayeeExtractor
import java.io.File

class RuleIOTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "notifledger-test-${System.currentTimeMillis()}")

    @Test
    fun `save and load parser rule`() {
        val rule = ParserRule(
            appName = "DNB",
            containsText = "Betalt",
            amountExtractor = AmountExtractor.FirstNumber,
            payeeExtractor = PayeeExtractor.BeforeAmount,
            currency = "NOK",
        )
        RuleIO.saveParserRule(tempDir, rule)
        val loaded = RuleIO.loadParserRules(tempDir)
        assertEquals(1, loaded.size)
        assertEquals("DNB", loaded[0].appName)
        assertEquals("Betalt", loaded[0].containsText)
        assertEquals(AmountExtractor.FirstNumber, loaded[0].amountExtractor)
        assertEquals(PayeeExtractor.BeforeAmount, loaded[0].payeeExtractor)
    }

    @Test
    fun `load empty directory returns empty list`() {
        val dir = File(tempDir, "empty")
        dir.mkdirs()
        assertEquals(emptyList<ParserRule>(), RuleIO.loadParserRules(dir))
    }

    @Test
    fun `save and load parser rule with after text extractor`() {
        val rule = ParserRule(
            appName = "Vipps",
            containsText = "Vipps",
            amountExtractor = AmountExtractor.LastNumber,
            payeeExtractor = PayeeExtractor.AfterText,
            amountPrefix = "på",
            currency = "NOK",
        )
        RuleIO.saveParserRule(tempDir, rule)
        val loaded = RuleIO.loadParserRules(tempDir)
        val vipps = loaded.find { it.appName == "Vipps" }
        assertTrue(vipps != null)
        assertEquals(AmountExtractor.LastNumber, vipps!!.amountExtractor)
        assertEquals(PayeeExtractor.AfterText, vipps.payeeExtractor)
        assertEquals("på", vipps.amountPrefix)
    }
}
