package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestItem
import com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestResponse
import com.example.gudgum_prod_flow.data.remote.dto.D2CRequestDto
import com.example.gudgum_prod_flow.data.remote.dto.FinishedGoodsAvailableRow
import com.example.gudgum_prod_flow.data.repository.D2CDispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the D2C dispatch flow on mobile:
 *   Step 1: pick channel (existing list + add-new)
 *   Step 2: per-flavour qty input; "Your pending requests" expandable
 *   Step 3: server-computed FIFO preview + Confirm
 */
@HiltViewModel
class D2CDispatchViewModel @Inject constructor(
    private val repo: D2CDispatchRepository,
) : ViewModel() {

    enum class Step { Channel, BuildRequest, Confirm, Done }

    private val _step = MutableStateFlow(Step.Channel)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val _channels = MutableStateFlow<List<String>>(emptyList())
    val channels: StateFlow<List<String>> = _channels.asStateFlow()

    private val _selectedChannel = MutableStateFlow<String>("")
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _flavors = MutableStateFlow<List<FinishedGoodsAvailableRow>>(emptyList())
    val flavors: StateFlow<List<FinishedGoodsAvailableRow>> = _flavors.asStateFlow()

    /** flavor_id → boxes typed by worker */
    private val _qtyByFlavor = MutableStateFlow<Map<String, Int>>(emptyMap())
    val qtyByFlavor: StateFlow<Map<String, Int>> = _qtyByFlavor.asStateFlow()

    private val _myPending = MutableStateFlow<List<D2CRequestDto>>(emptyList())
    val myPending: StateFlow<List<D2CRequestDto>> = _myPending.asStateFlow()

    private val _preview = MutableStateFlow<CreateD2CRequestResponse?>(null)
    val preview: StateFlow<CreateD2CRequestResponse?> = _preview.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    init { loadChannels(); loadMyPending() }

    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }

    fun loadChannels() {
        viewModelScope.launch {
            repo.getChannels().fold(
                onSuccess = { _channels.value = it },
                onFailure = { _error.value = "Failed to load channels: ${it.message}" },
            )
        }
    }

    fun loadMyPending() {
        viewModelScope.launch {
            repo.listMyPending().fold(
                onSuccess = { _myPending.value = it },
                onFailure = { /* silent — non-blocking */ },
            )
        }
    }

    /** Step 1 → 2: lock in the channel, load flavours w/ available stock. */
    fun selectChannel(channel: String) {
        if (channel.isBlank()) { _error.value = "Pick a channel."; return }
        _selectedChannel.value = channel
        _busy.value = true
        viewModelScope.launch {
            repo.getFinishedGoodsAvailable().fold(
                onSuccess = { rows ->
                    _flavors.value = rows.sortedBy { it.flavorName }
                    _step.value = Step.BuildRequest
                    _busy.value = false
                },
                onFailure = {
                    _error.value = "Failed to load flavours: ${it.message}"
                    _busy.value = false
                },
            )
        }
    }

    fun setQty(flavorId: String, qty: Int) {
        _qtyByFlavor.update { m ->
            if (qty <= 0) m - flavorId else m + (flavorId to qty)
        }
    }

    /** Returns true when worker has entered at least one positive qty AND none exceeds available. */
    fun reviewable(): Boolean {
        val m = _qtyByFlavor.value
        if (m.isEmpty()) return false
        val avail = _flavors.value.associate { it.flavorId to it.boxesAvailable }
        return m.all { (id, q) -> q > 0 && q <= (avail[id] ?: 0) }
    }

    /** Step 2 → 3: server-side create. */
    fun submit() {
        val items = _qtyByFlavor.value.map { CreateD2CRequestItem(flavorId = it.key, boxes = it.value) }
        if (items.isEmpty()) { _error.value = "Enter at least one flavour."; return }
        _busy.value = true
        viewModelScope.launch {
            repo.createRequest(_selectedChannel.value, items).fold(
                onSuccess = { r ->
                    _preview.value = r
                    _step.value = Step.Confirm
                    _busy.value = false
                    loadMyPending()
                },
                onFailure = {
                    _error.value = it.message
                    _busy.value = false
                },
            )
        }
    }

    /** Step 3 → Done: the create call already submitted; this is just confirmation. */
    fun finish() {
        _success.value = "Request submitted, awaiting admin approval."
        _step.value = Step.Done
    }

    /** Cancel an existing pending request (from the "Your pending" list). */
    fun cancelExistingRequest(requestId: String) {
        _busy.value = true
        viewModelScope.launch {
            repo.cancelRequest(requestId).fold(
                onSuccess = { _busy.value = false; loadMyPending() },
                onFailure = { _error.value = it.message; _busy.value = false },
            )
        }
    }

    /** Reset wizard. */
    fun reset() {
        _step.value = Step.Channel
        _selectedChannel.value = ""
        _qtyByFlavor.value = emptyMap()
        _preview.value = null
        _error.value = null
        _success.value = null
    }
}

private fun <K, V> MutableStateFlow<Map<K, V>>.update(block: (Map<K, V>) -> Map<K, V>) {
    this.value = block(this.value)
}
