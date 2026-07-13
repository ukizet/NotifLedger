package org.notifledger.app.parser

import org.notifledger.app.model.AmountExtractor
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.model.ParserRule
import org.notifledger.app.model.PayeeExtractor
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads and writes parser and categorization rules as YAML files
 * stored alongside the journal file.
 */
object RuleIO {

    private val yaml = Yaml()

    /**
     * Load all parser rule files from the rules directory.
     */
    fun loadParserRules(rulesDir: File): List<ParserRule> {
        if (!rulesDir.exists()) return emptyList()
        return rulesDir.listFiles { f -> f.extension in setOf("yaml", "yml") }
            ?.mapNotNull { file -> loadParserRule(file) }
            ?: emptyList()
    }

    private fun loadParserRule(file: File): ParserRule? {
        return try {
            val data = yaml.load<Map<String, Any>>(file.inputStream()) ?: return null
            ParserRule(
                appName = data["app"] as? String ?: return null,
                containsText = data["contains"] as? String ?: return null,
                amountExtractor = AmountExtractor.fromString(data["amount"] as? String ?: "first_number"),
                payeeExtractor = PayeeExtractor.fromString(data["payee"] as? String ?: "before_amount"),
                amountPrefix = data["amount_prefix"] as? String ?: "",
                currency = data["currency"] as? String ?: "NOK",
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a parser rule as a YAML file, named by app name.
     */
    fun saveParserRule(rulesDir: File, rule: ParserRule) {
        rulesDir.mkdirs()
        val data = linkedMapOf(
            "app" to rule.appName,
            "contains" to rule.containsText,
            "amount" to AmountExtractor.toRuleString(rule.amountExtractor),
            "payee" to PayeeExtractor.toRuleString(rule.payeeExtractor),
            "currency" to rule.currency,
        )
        if (rule.amountPrefix.isNotBlank()) data["amount_prefix"] = rule.amountPrefix
        val filename = "${rule.appName.lowercase().replace(" ", "_")}.yaml"
        File(rulesDir, filename).writeText(yaml.dumpAsMap(data))
    }

    /**
     * Load categorization rules from a single YAML file.
     */
    fun loadCategorizationRules(rulesDir: File): List<CategorizationRule> {
        val file = File(rulesDir, "categorization.yaml")
        if (!file.exists()) return emptyList()
        return try {
            val data = yaml.load<List<Map<String, Any>>>(file.inputStream())
            data.mapNotNull { entry ->
                val account = entry["account"] as? String ?: return@mapNotNull null
                CategorizationRule(
                    match = entry["match"] as? String ?: "",
                    account = account,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCategorizationRules(rulesDir: File, rules: List<CategorizationRule>) {
        rulesDir.mkdirs()
        val data = rules.map { rule ->
            val m = mutableMapOf<String, Any>()
            if (rule.match.isNotBlank()) m["match"] = rule.match
            m["account"] = rule.account
            m
        }
        File(rulesDir, "categorization.yaml").writeText(yaml.dumpAll(listOf(data).iterator()))
    }
}
