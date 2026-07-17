package org.notifledger.app.parser

import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.CategorizationRule
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads and writes categorization rules as YAML files.
 */
object RuleIO {

    private val yaml = Yaml()

    /**
     * Load categorization rules from a single YAML file.
     */
    fun loadCategorizationRules(rulesDir: File): List<CategorizationRule> {
        val file = File(rulesDir, "categorization.yaml")
        if (!file.exists()) return emptyList()
        return try {
            val data = file.inputStream().use { yaml.load<List<Map<String, Any>>>(it) }
            data.mapNotNull { entry ->
                val account = entry["account"] as? String ?: return@mapNotNull null
                CategorizationRule(
                    match = entry["match"] as? String ?: "",
                    account = account,
                )
            }
        } catch (e: Exception) {
            AppLogger.error("Rules", "Failed to load rules: ${e.message}")
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
        AppLogger.info("Rules", "Saved ${rules.size} rules")
    }
}
