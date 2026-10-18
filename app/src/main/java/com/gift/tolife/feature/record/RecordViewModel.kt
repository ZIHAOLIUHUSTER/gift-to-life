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
import com.gift.tolife.core.datastore.SettingsDataStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EntryWithTags(val entry: Entry, val tags: List<TagType>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val entryTransactions: EntryTransactions,
    @ApplicationContext private val context: Context,
    private val tagScheduler: TagWorkScheduler,
    private val imageStore: ImageStore,
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesPagingData: Flow<PagingData<EntryWithTags>> = _uiState
        .map { it.entryQuery }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                entryDao.pagingSource(EntryQuerySqlBuilder.build(query))
            }.flow.map { pagingData: PagingData<EntryListRow> ->
                pagingData.map { row ->
                    EntryWithTags(
                        entry = Entry(
                            id = row.id, content = row.content, imagePath = row.imagePath,
                            type = row.type, createdAt = row.createdAt, updatedAt = row.updatedAt,
                            imageDescription = row.imageDescription
                        ),
                        tags = row.tags()
                    )
                }
            }
        }

    private val _events = MutableSharedFlow<RecordEvent>()
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    private var searchGeneration = 0L
    private val searchText = MutableStateFlow(RecordInputPolicy.SearchInput("", searchGeneration))
    private var nextShareId = 0L
    private var nextFocusId = 0L

    init {
        consumeSharedContent()
        // 搜索防抖：250ms 后更新 entryQuery
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            searchText.debounce(250).collect { input ->
                _uiState.update { state ->
                    if (RecordInputPolicy.applySearch(input, searchGeneration, state.isSearchMode, state.searchQuery)) {
                        state.copy(entryQuery = state.entryQuery.copy(searchText = input.text))
                    } else state
                }
            }
        }
    }

    private fun consumeSharedContent() {
        viewModelScope.launch {
            ShareReceiver.events.collect { shared ->
                val state = _uiState.value
                when (RecordInputPolicy.incomingShare(
                    shared.text, shared.imageUri != null,
                    state.draftText, state.pendingImageUri != null, state.isSaving, state.pendingShares.isNotEmpty()
                )) {
                    RecordInputPolicy.ShareAction.FOCUS_ONLY -> requestComposerFocus()
                    RecordInputPolicy.ShareAction.RECEIVE -> acceptShare(PendingShare(++nextShareId, shared))
                    RecordInputPolicy.ShareAction.QUEUE -> {
                        val pending = PendingShare(++nextShareId, shared)
                        _uiState.update { it.copy(pendingShares = it.pendingShares + pending) }
                        requestComposerFocus()
                    }
                }
            }
        }
    }

    private fun requestComposerFocus() {
        closeSearch()
        val focusId = ++nextFocusId
        _uiState.update { it.copy(composerFocusRequest = focusId) }
    }

    private fun acceptShare(share: PendingShare) {
        closeSearch()
        val focusId = ++nextFocusId
        _uiState.update {
            it.copy(
                draftText = share.content.text.orEmpty(),
                pendingImageUri = share.content.imageUri,
                draftVersion = it.draftVersion + 1,
                saveError = null,
                pendingShares = it.pendingShares.filterNot { pending -> pending.id == share.id },
                composerFocusRequest = focusId
            )
        }
    }

    fun consumeComposerFocus(requestId: Long) {
        _uiState.update { if (it.composerFocusRequest == requestId) it.copy(composerFocusRequest = null) else it }
    }

    fun keepDraft(shareId: Long) {
        _uiState.update {
            if (RecordInputPolicy.canResolveShare(shareId, it.pendingShares.firstOrNull()?.id, it.isSaving)) {
                it.copy(pendingShares = it.pendingShares.drop(1))
            } else it
        }
    }

    fun saveDraftAndReceive(shareId: Long) {
        val state = _uiState.value
        if (!RecordInputPolicy.canResolveShare(shareId, state.pendingShares.firstOrNull()?.id, state.isSaving)) return
        saveDraftInternal(shareId)
    }

    private val saveMutex = Mutex()

    fun saveDraft() = saveDraftInternal()

    private fun saveDraftInternal(receiveShareId: Long? = null) {
        viewModelScope.launch {
            if (!saveMutex.tryLock()) return@launch
            var copiedImagePath: String? = null
            var persisted = false
            try {
                if (receiveShareId != null && _uiState.value.pendingShares.firstOrNull()?.id != receiveShareId) return@launch
                _uiState.update { it.copy(isSaving = true, saveError = null) }
                val snapshot = _uiState.value
                val pendingImage = snapshot.pendingImageUri
                val parsed = EntryInputParser.parse(snapshot.draftText)
                if (!parsed.canSave(pendingImage != null)) {
                    _uiState.update { it.copy(isSaving = false) }
                    if (receiveShareId != null) {
                        snapshot.pendingShares.firstOrNull()?.let(::acceptShare)
                    }
                    return@launch
                }
                val imagePath = pendingImage?.let { uri ->
                    ImageUtil.copyToPrivateDir(context, uri)
                }
                if (pendingImage != null && imagePath == null) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(RecordEvent.ShowSnackbar("图片处理失败，记录尚未保存"))
                    return@launch
                }
                copiedImagePath = imagePath
                val entry = Entry(
                    content = parsed.content,
                    imagePath = imagePath,
                    type = EntryType.NORMAL
                )
                val entryId = repository.save(entry, parsed.tags)
                persisted = true
                val beforeClear = _uiState.value
                val completion = RecordInputPolicy.completeSave(
                    true, snapshot.draftVersion, beforeClear.draftVersion, receiveShareId,
                    beforeClear.pendingShares.firstOrNull()?.id
                )
                val shareToAccept = if (completion.receivePendingShare) beforeClear.pendingShares.firstOrNull() else null

                _uiState.update {
                    if (completion.clearDraft && it.draftVersion == snapshot.draftVersion) {
                        it.copy(
                            draftText = "",
                            pendingImageUri = null,
                            isSaving = false,
                            draftVersion = it.draftVersion + 1
                        )
                    } else {
                        it.copy(isSaving = false)
                    }
                }
                shareToAccept?.let(::acceptShare)
                // Database success is final; background tagging/streak failure must not resurrect a saved draft.
                if (parsed.runAi) runCatching { tagScheduler.enqueue(entryId, 0L) }
                if (receiveShareId != null && shareToAccept == null) {
                    _events.emit(RecordEvent.ShowSnackbar("原草稿已保存；新修改仍保留，请再次接收分享"))
                } else {
                    _events.emit(RecordEvent.EntrySaved)
                }
                runCatching { updateStreak() }
            } catch (t: Throwable) {
                if (!persisted) copiedImagePath?.let { imageStore.delete(it) }
                if (t is CancellationException) throw t
                _uiState.update { it.copy(isSaving = false, saveError = t.message) }
                _events.emit(RecordEvent.ShowSnackbar("保存失败，草稿和待接收分享均已保留"))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                saveMutex.unlock()
            }
        }
    }

    fun setDraftText(text: String) {
        _uiState.update { it.copy(draftText = text, draftVersion = it.draftVersion + 1) }
    }

    fun selectImage(uri: android.net.Uri) {
        _uiState.update { it.copy(pendingImageUri = uri, draftVersion = it.draftVersion + 1) }
    }

    fun clearImage() {
        _uiState.update { it.copy(pendingImageUri = null, draftVersion = it.draftVersion + 1) }
    }

    fun setEditingImage(entry: Entry?) {
        _uiState.update { it.copy(editingImageEntry = entry) }
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

    fun delete(entry: Entry) {
        viewModelScope.launch {
            repository.softDelete(entry.id)
            _events.emit(RecordEvent.EntryMovedToRecycleBin(entry.id))
        }
    }

    fun restoreEntry(entryId: Long) {
        viewModelScope.launch {
            repository.restoreEntry(entryId)
            _events.emit(RecordEvent.ShowSnackbar("已恢复"))
        }
    }

    fun selectEntry(entry: Entry) {
        if (_uiState.value.isSavingEdit) return
        _uiState.update { it.copy(selectedEntry = entry, editingTagsEntryId = null) }
    }

    fun clearSelection() {
        _uiState.update {
            if (it.isSavingEdit) it else it.copy(selectedEntry = null, editingImageEntry = null, editingTagsEntryId = null)
        }
    }

    fun openSearch() {
        searchGeneration++
        searchText.value = RecordInputPolicy.SearchInput("", searchGeneration)
        _uiState.update { it.copy(isSearchMode = true, searchQuery = "", entryQuery = it.entryQuery.copy(searchText = "")) }
    }

    fun closeSearch() {
        searchGeneration++
        searchText.value = RecordInputPolicy.SearchInput("", searchGeneration)
        _uiState.update { it.copy(isSearchMode = false, searchQuery = "", entryQuery = EntryQuery()) }
    }

    fun setSearchQuery(query: String) {
        searchText.value = RecordInputPolicy.SearchInput(query, searchGeneration)
        _uiState.update { it.copy(searchQuery = query) }
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
        _uiState.update { it.copy(entryQuery = RecordInputPolicy.clearFilters(it.entryQuery)) }
    }

    private val _editTags = MutableStateFlow<List<TagType>>(emptyList())
    val editTags: StateFlow<List<TagType>> = _editTags.asStateFlow()

    private var _originalEditTags: Set<TagType> = emptySet()

    fun loadTags(entryId: Long) {
        viewModelScope.launch {
            val tags = repository.getTags(entryId).map { it.tag }
            // A late response for a previously closed entry must not reset the current edit draft.
            if (_uiState.value.selectedEntry?.id != entryId) return@launch
            _editTags.value = tags
            _originalEditTags = tags.toSet()
            _uiState.update { it.copy(editingTagsEntryId = entryId) }
        }
    }

    fun setEditTags(tags: List<TagType>) {
        _editTags.value = tags
    }

    fun saveEdit(draft: EntryEditDraft) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isSavingEdit || state.selectedEntry?.id != draft.entryId || state.editingTagsEntryId != draft.entryId) return@launch
            _uiState.update { it.copy(isSavingEdit = true) }
            var newImagePath: String? = null
            var persisted = false
            try {
                val originalTags = _originalEditTags.toSet()
                val tagsChanged = draft.tags != originalTags
                val contentChanged = draft.content != _uiState.value.selectedEntry?.content ||
                    draft.imageChange != ImageChange.Keep

                val finalPath = when (draft.imageChange) {
                    is ImageChange.Keep -> draft.originalImagePath
                    is ImageChange.Remove -> null
                    is ImageChange.Replace -> {
                        val path = ImageUtil.copyToPrivateDir(context, draft.imageChange.uri)
                            ?: throw IllegalStateException("图片处理失败")
                        newImagePath = path
                        path
                    }
                }

                when {
                    tagsChanged -> {
                        // 用户改了标签，用 updateUserEntryAndTags，不投递 AI
                        entryTransactions.updateUserEntryAndTags(
                            draft.entryId, draft.content, finalPath, draft.tags
                        )
                    }
                    contentChanged -> {
                        // 只改正文或图片，走 updateUserContent 并投递 AI
                        val revision = entryTransactions.updateUserContent(
                            draft.entryId, draft.content, finalPath
                        )
                        // A background scheduling error must not delete a successfully persisted replacement image.
                        persisted = true
                        runCatching { tagScheduler.enqueue(draft.entryId, revision) }
                    }
                    else -> { /* 什么都没变 */ }
                }

                persisted = true
                // 成功后清理旧图片
                if (draft.imageChange != ImageChange.Keep && !draft.originalImagePath.isNullOrBlank()) {
                    imageStore.delete(draft.originalImagePath)
                }

                _uiState.update {
                    it.copy(selectedEntry = null, editingImageEntry = null, editingTagsEntryId = null, isSavingEdit = false)
                }
                _events.emit(RecordEvent.ShowSnackbar("修改已保存"))
            } catch (t: Throwable) {
                if (!persisted) newImagePath?.let { imageStore.delete(it) }
                if (t is CancellationException) throw t
                _events.emit(RecordEvent.ShowSnackbar("保存失败，修改已保留: ${t.message ?: ""}"))
            } finally {
                _uiState.update { it.copy(isSavingEdit = false) }
            }
        }
    }

    private suspend fun updateStreak() = withContext(Dispatchers.IO) {
        val today = java.time.LocalDate.now()
        val todayEpochDay = today.toEpochDay()
        val lastDay = settingsDataStore.getLastActiveDay()
        val currentStreak = settingsDataStore.getStreakCount()

        when {
            todayEpochDay == lastDay -> { /* 同一天，不更新 */ }
            todayEpochDay == lastDay + 1 -> {
                settingsDataStore.updateStreak(todayEpochDay, currentStreak + 1)
            }
            else -> {
                settingsDataStore.updateStreak(todayEpochDay, 1)
            }
        }
    }
}