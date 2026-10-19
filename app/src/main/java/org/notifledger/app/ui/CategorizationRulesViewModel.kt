package org.notifledger.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.notifledger.app.NotifLedgerApp
import org.notifledger.app.log.AppLogger
import org.notifledger.app.model.CategorizationRule
import org.notifledger.app.parser.RuleIO
import java.io.File

/** State for the categorization rules screen. */
class CategorizationRulesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<NotifLedgerApp>()

    private val _categorizationRules = MutableStateFlow<List<CategorizationRule>>(emptyList())
    val categorizationRules: StateFlow<List<CategorizationRule>> = _categorizationRules.asStateFlow()

    val accountSuggestions: StateFlow<List<String>> = app.journalSession.entries.map { entries ->
        entries.accountSuggestions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val rulesDir = getRulesDir()
            _categorizationRules.value = RuleIO.loadCategorizationRules(rulesDir)
            AppLogger.info("Rules", "Loaded ${_categorizationRules.value.size} categorization rules")
        }
    }

    fun saveCategorizationRules(rules: List<CategorizationRule>) {
        app.writeScope.launch {
            val rulesDir = getRulesDir()
            RuleIO.saveCategorizationRules(rulesDir, rules)
            _categorizationRules.value = RuleIO.loadCategorizationRules(rulesDir)
        }
    }

    fun getCachedCategorizationRules(): List<CategorizationRule> = _categorizationRules.value

    private fun getRulesDir(): File {
        val dir = File(getApplication<Application>().filesDir, "rules")
        dir.mkdirs()
        return dir
    }
}
