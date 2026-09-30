package com.burton.meeting.ui.call

import androidx.lifecycle.ViewModel
import com.burton.meeting.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val meetings: MeetingRepository,
) : ViewModel() {
    val session = meetings.session

    fun setMuted(muted: Boolean) = meetings.setMuted(muted)
    fun setCameraEnabled(enabled: Boolean) = meetings.setCameraEnabled(enabled)
    fun setSpeakerOn(on: Boolean) = meetings.setSpeakerOn(on)
    fun leave() = meetings.leave()
}
