package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.VodItem

/**
 * Converts between what the portal lists and what a favorite stores, so a favorite can be opened again later
 * without browsing to it. Plain Kotlin, tested without Android. The key fields (profile, kind, id) are filled in
 * by [ProfileRepository.setFavorite].
 */
object FavoriteItems {
    /** A MOVIE for a title, a SHOW for a series entry (the portal keeps shows among the movies). */
    fun kindOf(item: VodItem): String = if (item.isSeries) Kinds.SHOW else Kinds.MOVIE

    fun ofChannel(c: LiveChannel) = FavoriteEntity(0, Kinds.CHANNEL, c.id, c.name, null, 0, playCmd = c.cmd, number = c.number)

    fun ofVod(v: VodItem) = FavoriteEntity(
        0, kindOf(v), v.id, v.name, v.posterUri, 0, playCmd = v.cmd ?: v.fileCmds.firstOrNull(), year = v.year,
        description = v.description, actors = v.actors, director = v.director, genres = v.genres, age = v.age,
        imdbRating = v.imdbRating, kinopoiskRating = v.kinopoiskRating,
    )

    fun toChannel(f: FavoriteEntity) = LiveChannel(f.refId, f.number.orEmpty(), f.name, f.playCmd)

    fun toVod(f: FavoriteEntity) = VodItem(
        id = f.refId, name = f.name, isSeries = f.kind == Kinds.SHOW, isSeason = false, isEpisode = false, cmd = f.playCmd,
        episodeNumbers = emptyList(), year = f.year, description = f.description, actors = f.actors, director = f.director,
        genres = f.genres, imdbRating = f.imdbRating, kinopoiskRating = f.kinopoiskRating, age = f.age, posterUri = f.posterUri,
    )

    /** Channels in channel-number order (unnumbered last), which is the order the zapper steps through. */
    fun channelsInOrder(all: List<FavoriteEntity>): List<FavoriteEntity> =
        all.filter { it.kind == Kinds.CHANNEL }.sortedWith(compareBy({ it.number?.toIntOrNull() ?: Int.MAX_VALUE }, { it.name.lowercase() }))
}
