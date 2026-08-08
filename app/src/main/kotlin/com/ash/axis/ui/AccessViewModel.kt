package com.ash.axis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.session.Access
import com.ash.axis.data.session.AxisSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessViewModel
    @Inject
    constructor(
        private val repository: AxisSessionRepository,
    ) : ViewModel() {
        val state: StateFlow<Access> = repository.state

        fun refresh() {
            viewModelScope.launch { repository.refresh() }
        }
    }
