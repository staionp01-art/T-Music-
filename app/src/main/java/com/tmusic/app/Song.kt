package com.tmusic.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long, val title: String, val artist: String,
    val album: String, val albumId: Long, val duration: Long
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val art: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

fun loadSongs(ctx: Context): List<Song> {
    val out = mutableListOf<Song>()
    val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION
    )
    val sel = "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>30000"
    ctx.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, sel, null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
    )?.use { c ->
        while (c.moveToNext()) out += Song(
            c.getLong(0), c.getString(1) ?: "بدون عنوان", c.getString(2) ?: "فنان غير معروف",
            c.getString(3) ?: "", c.getLong(4), c.getLong(5)
        )
    }
    return out
}
