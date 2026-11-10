// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-device library database. It is never backed up or transferred (see data_extraction_rules.xml).
 * Future schema changes must ship a Migration so playlists and history are never wiped.
 */
@Database(
    entities = [
        FolderEntity::class,
        ExcludedFolderEntity::class,
        TrackEntity::class,
        TrackFtsEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ReleaseOverrideEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        PlayStatsEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PocketBoxDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun scanDao(): ScanDao
    abstract fun userDataDao(): UserDataDao

    companion object {
        private const val NAME = "pocketbox.db"

        fun create(context: Context): PocketBoxDatabase =
            Room.databaseBuilder(context.applicationContext, PocketBoxDatabase::class.java, NAME).build()
    }
}
