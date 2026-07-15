package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class OnThisDayState(
    val yearEntries: Map<Int, List<Entry>> = emptyMap(),
    val loading: Boolean = false
)

@HiltViewModel
class OnThisDayViewModel @Inject constructor(
    private val entryDao: EntryDao,
    private val repository: com.gift.tolife.core.database.EntryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnThisDayState())
    val state: StateFlow<OnThisDayState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = OnThisDayState(loading = true)
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH) + 1

            val monthDay = "%02d-%02d".format(month, today)
            val entries = entryDao.getEntriesByMonthDay(monthDay)

            if (entries.isEmpty()) {
                _state.value = OnThisDayState()
                return@launch
            }

            val result = mutableMapOf<Int, MutableList<Entry>>()
            entries.forEach { entry ->
                val entryCal = Calendar.getInstance().apply { timeInMillis = entry.createdAt }
                val year = entryCal.get(Calendar.YEAR)
                result.getOrPut(year) { mutableListOf() }.add(entry)
            }
            _state.value = OnThisDayState(yearEntries = result)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repository.softDelete(entryId)
        }
    }
}