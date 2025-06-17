package io.github.gauravyad69.speakershare.music

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import io.github.gauravyad69.speakershare.audio.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicLibrary(private val context: Context) {
    
    suspend fun loadLocalMusic(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )
        
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn) ?: "Unknown Title"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val duration = it.getLong(durationColumn)
                val data = it.getString(dataColumn)
                
                tracks.add(
                    AudioTrack(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        uri = data,
                        duration = duration
                    )
                )
            }
        }
        
        tracks
    }
    
    fun getSampleTracks(): List<AudioTrack> {
        return listOf(
            AudioTrack(
                id = "sample_1",
                title = "Demo Track 1",
                artist = "Sample Artist",
                uri = "android.resource://${context.packageName}/raw/sample_track",
                duration = 180000L
            ),
            AudioTrack(
                id = "sample_2",
                title = "Demo Track 2",
                artist = "Another Artist",
                uri = "android.resource://${context.packageName}/raw/sample_track_2",
                duration = 200000L
            )
        )
    }
}