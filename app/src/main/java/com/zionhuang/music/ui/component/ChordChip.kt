package com.zionhuang.music.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A minimal text component for displaying guitar chord names.
 *
 * Designed as a non-interactive, minimal text display without background.
 * Uses primary color for visibility without overpowering lyrics.
 *
 * @param chord The chord name to display (e.g., "Em7", "Cadd9", "Bm7")
 * @param modifier Optional modifier for positioning and sizing
 */
@Composable
fun ChordChip(
    chord: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = chord,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        maxLines = 1
    )
}

@Preview(name = "Chord Chips")
@Composable
private fun ChordChipPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            ChordChip("Em7")
            ChordChip("Cadd9")
            ChordChip("Bm7")
            ChordChip("Gmaj7sus4")
            ChordChip("F#m")
        }
    }
}
