package org.notifledger.app.filters

import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.TransactionFilter
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads and writes transaction filters as YAML files.
 */
object FilterIO {

    private val yaml = Yaml()

    /**
     * Load transaction filters from a single YAML file.
     */
    fun loadFilters(dir: File): List<TransactionFilter> {
        val file = File(dir, "filters.yaml")
        if (!file.exists()) return emptyList()
        return try {
            val data = file.inputStream().use { yaml.load<List<Map<String, Any>>>(it) }
            data.mapNotNull { entry ->
                val label = entry["label"] as? String ?: return@mapNotNull null
                val account = entry["account"] as? String ?: return@mapNotNull null
                TransactionFilter(
                    label = label,
                    account = account,
                    isActive = entry["active"] as? Boolean ?: true,
                )
            }
        } catch (e: Exception) {
            AppLogger.error("Filters", "Failed to load filters: ${e.message}")
            emptyList()
        }
    }

    fun saveFilters(dir: File, filters: List<TransactionFilter>) {
        dir.mkdirs()
        val data = filters.map { filter ->
            val m = mutableMapOf<String, Any>()
            m["label"] = filter.label
            m["account"] = filter.account
            m["active"] = filter.isActive
            m
        }
        File(dir, "filters.yaml").writeText(yaml.dumpAll(listOf(data).iterator()))
        AppLogger.info("Filters", "Saved ${filters.size} filters")
    }
}
