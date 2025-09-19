package com.zionhuang.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chords")
data class ChordsEntity(
    @PrimaryKey val id: String,
    val chords: String,
) {
    companion object {
        const val CHORDS_NOT_FOUND = "CHORDS_NOT_FOUND"
    }
}
