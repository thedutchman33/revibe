package com.revibe.catalog

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Artist biographies from Wikipedia (CC BY-SA 4.0). The UI always shows the
 * attribution from [ArtistKnowledge.sourceLabel].
 */
class WikipediaArtistKnowledge(private val client: HttpClient) : ArtistKnowledgeSource {

    override suspend fun biography(artistName: String): ArtistKnowledge? {
        if (artistName.isBlank()) return null
        val title = artistName.trim().replace(' ', '_')
        return runCatching {
            val response: HttpResponse = client.get(
                "https://en.wikipedia.org/api/rest_v1/page/summary/${title.encodeURLPathPart()}",
            )
            if (!response.status.isSuccess()) return@runCatching null
            val page: WikipediaSummary = response.body()
            val extract = page.extract?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val isDisambiguation = page.type == "disambiguation"
            if (isDisambiguation) return@runCatching null
            ArtistKnowledge(
                summary = extract,
                imageUrl = page.thumbnail?.source ?: page.originalimage?.source,
                pageUrl = page.content_urls?.desktop?.page
                    ?: "https://en.wikipedia.org/wiki/${title.encodeURLPathPart()}",
                sourceLabel = "Wikipedia, CC BY-SA 4.0",
            )
        }.getOrNull()
    }
}

@Serializable
private data class WikipediaSummary(
    val title: String? = null,
    val extract: String? = null,
    val type: String? = null,
    val thumbnail: WikipediaImage? = null,
    val originalimage: WikipediaImage? = null,
    val content_urls: WikipediaContentUrls? = null,
)

@Serializable
private data class WikipediaImage(val source: String? = null, val width: Int = 0, val height: Int = 0)

@Serializable
private data class WikipediaContentUrls(val desktop: WikipediaPage? = null)

@Serializable
private data class WikipediaPage(val page: String? = null)
