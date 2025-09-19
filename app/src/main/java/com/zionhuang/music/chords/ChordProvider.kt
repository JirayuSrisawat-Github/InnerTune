package com.zionhuang.music.chords

import android.content.Context

interface ChordProvider {
    val name: String
    fun isEnabled(context: Context): Boolean
    suspend fun getChords(id: String, title: String, artist: String): Result<String>
}
