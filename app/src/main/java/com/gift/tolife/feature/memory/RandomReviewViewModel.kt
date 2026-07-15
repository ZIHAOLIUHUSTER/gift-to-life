package com.gift.tolife.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.TagType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RandomReviewState(
    val entry: Entry? = null,
    val tags: List<TagType> = emptyList()
)

@HiltViewModel
class RandomReviewViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val entryDao: EntryDao
) : ViewModel() {

    private val _state = MutableStateFlow(RandomReviewState())
    val state: StateFlow<RandomReviewState> = _state.asStateFlow()

    init {
        fetchRandom()
    }

    fun fetchRandom() {
        viewModelScope.launch {
            val count = entryDao.getActiveEntryCount()
            if (count == 0) return@launch
            val offset = kotlin.random.Random.nextInt(count)
            val entry = entryDao.getEntryAtOffset(offset)
            if (entry != null) {
                val tags = repository.getTags(entry.id).map { it.tag }
                _state.update { RandomReviewState(entry, tags) }
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repository.softDelete(entryId)
        }
    }
}
