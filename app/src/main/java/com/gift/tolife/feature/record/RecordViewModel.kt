package com.gift.tolife.feature.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.gift.tolife.core.ai.TagWorkScheduler
import com.gift.tolife.core.common.ImageStore
import com.gift.tolife.core.common.ImageUtil
import com.gift.tolife.core.common.ShareReceiver
import com.gift.tolife.core.database.EntryRepository
import com.gift.tolife.core.database.EntryTransactions
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.database.model.EntryListRow
import com.gift.tolife.core.database.query.EntryQuerySqlBuilder
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
    private val imageStore: ImageStore,
    private val entryDao: EntryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesPagingData: Flow<PagingData<Entry>> = _uiState
        .map { it.entryQuery }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                entryDao.pagingSource(EntryQuerySqlBuilder.build(query))
            }.flow.map { pagingData: PagingData<EntryListRow> ->
                pagingData.map { row ->
                    Entry(
                        id = row.id, content = row.content, imagePath = row.imagePath,
                        type = row.type, createdAt = row.createdAt, updatedAt = row.updatedAt,
                        imageDescription = row.imageDescription
                    )
                }
            }
        }

    private val _events = MutableSharedFlow<RecordEvent>()
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    init {
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
        val pendingImage = _uiState.value.pendingImageUri
        if (!EntrySavePolicy.canSave(content, pendingImage != null)) return
        viewModelScope.launch {
            val imagePath = pendingImage?.let { uri ->
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

    fun setEditingImage(entry: Entry?) {
        _uiState.update { it.copy(editingImageEntry = entry) }
    }

    fun removeImage(entry: Entry) {
        viewModelScope.launch {
            val oldPath = entry.imagePath
            val revision = entryTransactions.updateUserContent(
                entry.id, entry.content, null
            )
            val deleted = imageStore.delete(oldPath)
            if (!deleted && !oldPath.isNullOrBlank()) {
                _events.emit(RecordEvent.ShowSnackbar("记录已更新，旧图片将在后续清理"))
            }
            tagScheduler.enqueue(entry.id, revision)
            _uiState.update { state ->
                state.copy(
                    selectedEntry = state.selectedEntry?.copy(imagePath = null, imageDescription = null)
                )
            }
        }
    }

    fun replaceImage(entry: Entry, uri: android.net.Uri) {
        viewModelScope.launch {
            val newPath = ImageUtil.copyToPrivateDir(context, uri)
            if (newPath == null) {
                _events.emit(RecordEvent.ShowSnackbar("图片处理失败"))
                return@launch
            }
            val oldPath = entry.imagePath
            val revision = entryTransactions.updateUserContent(
                entry.id, entry.content, newPath
            )
            imageStore.delete(oldPath)
            tagScheduler.enqueue(entry.id, revision)
            _uiState.update { state ->
                state.copy(
                    selectedEntry = state.selectedEntry?.copy(imagePath = newPath),
                    editingImageEntry = null
                )
            }
        }
    }

    fun update(entry: Entry) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(selectedEntry = null)
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
            val original = _uiState.value.selectedEntry ?: return@launch
            val oldTags = repository.getTags(entry.id).map { it.tag }.toSet()
            val newTags = tags.toSet()
            val contentChanged = entry.content != original.content ||
                entry.imagePath != original.imagePath

            when {
                oldTags != newTags -> {
                    // 用户显式修改标签，不投递 AI
                    entryTransactions.updateUserEntryAndTags(
                        entry.id, entry.content, entry.imagePath, newTags
                    )
                }
                contentChanged -> {
                    // 正文或图片变化，投递 AI 重新打标签
                    val revision = entryTransactions.updateUserContent(
                        entry.id, entry.content, entry.imagePath
                    )
                    tagScheduler.enqueue(entry.id, revision)
                }
                else -> {
                    // 什么都没变
                }
            }
            _uiState.update { it.copy(selectedEntry = null) }
        }
    }
}
