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
    private val entryDao: EntryDao
) : ViewModel() {

    private val _state = MutableStateFlow(OnThisDayState())
    val state: StateFlow<OnThisDayState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = OnThisDayState(loading = true)
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH)
            val thisYear = cal.get(Calendar.YEAR)

            if (month == Calendar.FEBRUARY && today == 29 && !cal.getActualMaximum(Calendar.DAY_OF_MONTH).let { it >= 29 }) {
                _state.value = OnThisDayState()
                return@launch
            }

            // 查最早记录年份
            val firstTimestamp = entryDao.getFirstEntryTimestamp()
            val firstYear = if (firstTimestamp != null) {
                Calendar.getInstance().apply { timeInMillis = firstTimestamp }.get(Calendar.YEAR)
            } else {
                _state.value = OnThisDayState()
                return@launch
            }

            val result = mutableMapOf<Int, List<Entry>>()
            for (year in thisYear - 1 downTo firstYear) {
                if (month == Calendar.FEBRUARY && today == 29) {
                    val yearCal = Calendar.getInstance().apply { set(Calendar.YEAR, year) }
                    if (yearCal.getActualMaximum(Calendar.DAY_OF_MONTH) < 29) continue
                }

                val startCal = Calendar.getInstance().apply {
                    set(year, month, today, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(year, month, today, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val entries = entryDao.getEntriesByDateRange(startCal.timeInMillis, endCal.timeInMillis)
                if (entries.isNotEmpty()) {
                    result[year] = entries
                }
            }
            _state.value = OnThisDayState(yearEntries = result)
        }
    }
}