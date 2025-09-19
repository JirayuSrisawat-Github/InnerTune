package com.zionhuang.music.chords

import android.content.Context
import com.zionhuang.music.db.entities.ChordsEntity.Companion.CHORDS_NOT_FOUND
import com.zionhuang.music.models.MediaMetadata
import com.zionhuang.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ChordsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val providers: List<ChordProvider> = listOf(DochordChordProvider)

    suspend fun getChords(mediaMetadata: MediaMetadata): String {
        val artist = mediaMetadata.artists.joinToString { it.name }
        providers.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getChords(mediaMetadata.id, mediaMetadata.title, artist)
                    .onSuccess { chords ->
                        return chords
                    }.onFailure(::reportException)
            }
        }
        return CHORDS_NOT_FOUND
    }
}
