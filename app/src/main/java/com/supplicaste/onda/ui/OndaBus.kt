package com.supplicaste.onda.ui

import kotlinx.coroutines.flow.MutableSharedFlow

object OndaBus {
    val imported = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
