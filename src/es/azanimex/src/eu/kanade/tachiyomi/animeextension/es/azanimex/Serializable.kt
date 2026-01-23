package eu.kanade.tachiyomi.animeextension.es.azanimex

import kotlinx.serialization.Serializable

@Serializable
data class DirectorioResponse(
    val data: Content,
)

@Serializable
data class Content(
    val content: List<EntryInfo>,
)

@Serializable
data class EntryInfo(
    val path: String, // "/Rozen Maiden - Träumend BD/[Az-Animex] Rozen Maiden - Träumend - 01 [BD][Otsalia].mp4",
    val name: String, // "[Az-Animex] Rozen Maiden - Träumend - 01 [BD][Otsalia].mp4",
    val size: Int, // 142863726,
    val is_dir: Boolean,
    val modified: String, // "2026-01-21T00:00:53Z",
    val created: String, // "2026-01-21T00:00:53Z",
    val thumb: String?, // "https://www.mediafire.com/convkey/acaa/nr9k4nn0ezmhfzm3g.jpg",
    val type: Int?, // 2,
)
