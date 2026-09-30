package com.burton.meeting.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.meeting.data.repository.MeetingRepository
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.NearbyMeeting
import com.burton.meeting.domain.RoomCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val meetings: MeetingRepository,
) : ViewModel() {
    val nearby = meetings.nearby
    val displayName = meetings.displayName
    val session = meetings.session

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        meetings.startBrowsing()
    }

    fun setDisplayName(name: String) {
        viewModelScope.launch { meetings.setDisplayName(name) }
    }

    fun startMeeting(name: String, mode: CallMode) {
        viewModelScope.launch {
            _error.value = null
            runCatching { meetings.host(name, mode) }
                .onFailure { _error.value = it.message ?: "Could not start the meeting" }
        }
    }

    fun joinMeeting(meeting: NearbyMeeting, mode: CallMode) {
        viewModelScope.launch {
            _error.value = null
            runCatching { meetings.join(meeting, mode) }
                .onFailure { _error.value = it.message ?: "Could not join" }
        }
    }

    fun joinByCode(code: String, mode: CallMode) {
        viewModelScope.launch {
            _error.value = null
            if (!RoomCodes.isValid(code)) {
                _error.value = "Enter a 6-character meeting code"
                return@launch
            }
            runCatching { meetings.joinByCode(code, mode) }
                .onFailure { _error.value = it.message ?: "No meeting with that code on this Wi-Fi" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
