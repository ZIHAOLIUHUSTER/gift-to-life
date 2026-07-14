package com.gift.tolife.feature.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gift.tolife.core.ai.TagWorkScheduler
import com.gift.tolife.core.common.ImageStore
import com.gift.tolife.core.common.ImageUtil
import com.gift.tolife.core.common.ShareReceiver
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.EntryTransactions
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryQuery
import com.gift.tolife.core.model.EntryType
import com.gift.tolife.core.model.TagType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val entryTransactions: EntryTransactions,
    @ApplicationContext private val context: Context,
    private val tagScheduler: TagWorkScheduler,
    private val imageStore: ImageStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>()
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.entryQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    repository.getFilteredEntries(query)
                }
                .collect { entries ->
                    // 批量加载所有条目的标签
                    val tagsMap = repository.getTagsBatch(entries.map { it.id })
                    _uiState.update { it.copy(entries = entries, entryTags = tagsMap, isLoading = false) }
                }
        }
        consumeSharedContent()
    }

    private fun consumeSharedContent() {
        viewModelScope.launch {
            ShareReceiver.events.collect { shared ->
                _uiState.update { it.copy(
                    pendingContentText = shared.text ?: it.pendingContentText,
                    pendingImageUri = shared.imageUri ?: it.pendingImageUri
                ) }
            }
        }
    }

    fun save(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val currentState = _uiState.value
            val imagePath = currentState.pendingImageUri?.let { uri ->
                ImageUtil.copyToPrivateDir(context, uri)
            }

            val entry = Entry(
                content = content.trim(),
                imagePath = imagePath,
                type = EntryType.NORMAL
            )
            val entryId = repository.save(entry)
            tagScheduler.enqueue(entryId, 0L)
            _uiState.update { it.copy(pendingImageUri = null, pendingContentText = null) }
        }
    }

    fun selectImage(uri: android.net.Uri) {
        _uiState.update { it.copy(pendingImageUri = uri) }
    }

    fun clearImage() {
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    fun setEditingImage(entryId: Long?) {
        _uiState.update { it.copy(editingImageEntryId = entryId) }
    }

    fun removeImage(entry: Entry) {
        viewModelScope.launch {
            val oldPath = entry.imagePath
            val updated = entry.copy(imagePath = null, imageDescription = null)
            // 乐观更新本地列表，避免等待 Flow 重发
            _uiState.update { state ->
                state.copy(
                    entries = state.entries.map { if (it.id == entry.id) updated else it },
                    selectedEntry = if (state.selectedEntry?.id == entry.id) updated else state.selectedEntry
                )
            }
            // 删除旧图片文件
            imageStore.delete(oldPath)

            val newRevision = entryTransactions.updateUserContent(entry.id, entry.content, null)
            tagScheduler.enqueue(entry.id, newRevision)
        }
    }

    fun replaceImage(entry: Entry, uri: android.net.Uri) {
        viewModelScope.launch {
            val oldPath = entry.imagePath
            val imagePath = ImageUtil.copyToPrivateDir(context, uri)
            if (imagePath != null) {
                val updated = entry.copy(imagePath = imagePath)
                // 乐观更新本地列表和编辑状态
                _uiState.update { state ->
                    state.copy(
                        entries = state.entries.map { if (it.id == entry.id) updated else it },
                        selectedEntry = if (state.selectedEntry?.id == entry.id) updated else state.selectedEntry,
                        editingImageEntryId = null
                    )
                }
                // 删除旧图片文件
                imageStore.delete(oldPath)

                val newRevision = entryTransactions.updateUserContent(entry.id, entry.content, imagePath)
                tagScheduler.enqueue(entry.id, newRevision)
            }
        }
    }

    fun update(entry: Entry) {
        viewModelScope.launch {
            // 乐观更新本地列表，避免等待 Flow 重发
            _uiState.update { state ->
                state.copy(
                    entries = state.entries.map { if (it.id == entry.id) entry else it },
                    selectedEntry = null
                )
            }
            val newRevision = entryTransactions.updateUserContent(entry.id, entry.content, entry.imagePath)
            tagScheduler.enqueue(entry.id, newRevision)
        }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch {
            repository.softDelete(entry.id)
            _events.emit(RecordEvent.ShowSnackbar("已移至回收站"))
        }
    }

    fun selectEntry(entry: Entry) {
        _uiState.update { it.copy(selectedEntry = entry) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntry = null) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isSearchMode = true, searchQuery = "") }
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchMode = false, searchQuery = "", entryQuery = EntryQuery()) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { 
            it.copy(
                searchQuery = query, 
                entryQuery = it.entryQuery.copy(searchText = query)
            ) 
        }
    }

    fun setFilterStartDate(date: Long?) {
        _uiState.update { it.copy(entryQuery = it.entryQuery.copy(startDate = date)) }
    }

    fun setFilterEndDate(date: Long?) {
        _uiState.update { it.copy(entryQuery = it.entryQuery.copy(endDate = date)) }
    }

    fun setFilterHasImage(hasImage: Boolean?) {
        _uiState.update { it.copy(entryQuery = it.entryQuery.copy(hasImage = hasImage)) }
    }

    fun toggleTagFilter(tag: TagType) {
        _uiState.update { state ->
            val current = state.entryQuery.selectedTags
            val updated = if (tag in current) current - tag else current + tag
            state.copy(entryQuery = state.entryQuery.copy(selectedTags = updated))
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(entryQuery = EntryQuery()) }
    }

    private val _editTags = MutableStateFlow<List<TagType>>(emptyList())
    val editTags: StateFlow<List<TagType>> = _editTags.asStateFlow()

    fun loadTags(entryId: Long) {
        viewModelScope.launch {
            val tags = repository.getTags(entryId).map { it.tag }
            _editTags.value = tags
        }
    }

    fun setEditTags(tags: List<TagType>) {
        _editTags.value = tags
    }

    fun saveWithTags(entry: Entry, tags: List<TagType>) {
        viewModelScope.launch {
            repository.setTags(entry.id, tags)
            update(entry)
        }
    }
}
