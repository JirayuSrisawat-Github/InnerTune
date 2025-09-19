package com.zionhuang.music.chords

import android.content.Context
import com.zionhuang.dochord.Dochord

object DochordChordProvider : ChordProvider {
    override val name: String = "Dochord"

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getChords(id: String, title: String, artist: String): Result<String> =
        Dochord.fetchChordSheet(title, artist).map { it.text }
}
