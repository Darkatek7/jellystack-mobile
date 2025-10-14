@file:Suppress("FunctionName")

package dev.jellystack.design.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinLibrary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun JellyfinBrowseScreen(
    state: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (JellyfinItem) -> Unit,
    onConnectServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedLibrary =
        remember(state.libraries, state.selectedLibraryId) {
            state.libraries.firstOrNull { it.id == state.selectedLibraryId }
        }
    val isTvLibrary =
        selectedLibrary
            ?.collectionType
            ?.equals("tvshows", ignoreCase = true) == true ||
            selectedLibrary
                ?.collectionType
                ?.equals("series", ignoreCase = true) == true ||
            state.libraryItems.any { item ->
                item.type.equals("Series", ignoreCase = true) || item.type.equals("Episode", ignoreCase = true)
            }
    val seriesGroups =
        remember(state.libraryItems, isTvLibrary) {
            if (isTvLibrary) groupTvSeries(state.libraryItems) else emptyList()
        }
    LoadMoreListener(
        listState = listState,
        shouldLoadMore = !state.endReached && !state.isPageLoading && !state.isInitialLoading && state.libraryItems.isNotEmpty(),
        onLoadMore = onLoadMore,
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "libraries") {
                LibrarySelector(
                    libraries = state.libraries,
                    selectedLibraryId = state.selectedLibraryId,
                    onSelectLibrary = onSelectLibrary,
                )
            }
            if (state.continueWatching.isNotEmpty()) {
                item(key = "continueWatching") {
                    ContinueWatchingSection(
                        items = state.continueWatching,
                        baseUrl = state.imageBaseUrl,
                        accessToken = state.imageAccessToken,
                        onItemSelected = onOpenDetail,
                    )
                }
            }
            item(key = "status") {
                StatusBanner(
                    state = state,
                    onRetry = onRefresh,
                    onConnect = onConnectServer,
                )
            }
            if (isTvLibrary) {
                items(
                    items = seriesGroups,
                    key = { it.id },
                ) { group ->
                    TvSeriesCard(
                        group = group,
                        baseUrl = state.imageBaseUrl,
                        accessToken = state.imageAccessToken,
                        onOpenSeries = { series ->
                            onOpenDetail(series)
                        },
                        onOpenEpisode = onOpenDetail,
                    )
                }
            } else {
                items(
                    items = state.libraryItems,
                    key = { it.id },
                ) { item ->
                    LibraryItemRow(
                        item = item,
                        baseUrl = state.imageBaseUrl,
                        accessToken = state.imageAccessToken,
                        onClick = { onOpenDetail(item) },
                    )
                }
            }
            if (state.isPageLoading) {
                item(key = "pagingLoader") {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        if (state.isInitialLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun LibrarySelector(
    libraries: List<JellyfinLibrary>,
    selectedLibraryId: String?,
    onSelectLibrary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (libraries.isEmpty()) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("No Jellyfin libraries linked yet") },
            modifier = modifier,
        )
        return
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(libraries, key = { it.id }) { library ->
            FilterChip(
                selected = library.id == selectedLibraryId,
                onClick = { onSelectLibrary(library.id) },
                label = { Text(library.name) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    onItemSelected: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("${items.size}") },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ContinueWatchingCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(160.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box {
            val seriesItemId = item.seriesId ?: item.parentId
            val fallbackCandidates =
                buildList<ImageCandidate> {
                    addCandidate(item.id, item.primaryImageTag, "Primary")
                    addCandidate(item.id, item.thumbImageTag, "Thumb")
                    addCandidate(item.id, item.backdropImageTag, "Backdrop")
                }
            PosterImage(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                baseUrl = baseUrl,
                itemId = seriesItemId ?: item.id,
                primaryTag = item.seriesPrimaryImageTag ?: item.primaryImageTag,
                thumbTag = item.seriesThumbImageTag ?: item.thumbImageTag,
                backdropTag = item.seriesBackdropImageTag ?: item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
                primaryImageItemId = seriesItemId,
                thumbImageItemId = seriesItemId,
                backdropImageItemId = seriesItemId,
                logoImageItemId = seriesItemId,
                logoTag = item.parentLogoImageTag,
                extraCandidates = fallbackCandidates,
            )
            val progress = progressFraction(item)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.seriesName ?: item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.seriesName.isNullOrBlank()) {
                Text(
                    text = item.episodeTitle ?: item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class TvSeriesGroup(
    val id: String,
    val series: JellyfinItem?,
    val episodes: List<JellyfinItem>,
) {
    val fallbackEpisode: JellyfinItem?
        get() = episodes.firstOrNull()
    val title: String
        get() = series?.name ?: fallbackEpisode?.seriesName ?: fallbackEpisode?.name ?: "Series"
    val overview: String?
        get() = series?.overview ?: fallbackEpisode?.overview
    val posterItemId: String
        get() = primaryImageItemId ?: series?.id ?: fallbackEpisode?.id ?: id
    val primaryImageTag: String?
        get() =
            series?.primaryImageTag
                ?: series?.seriesPrimaryImageTag
                ?: fallbackEpisode?.seriesPrimaryImageTag
                ?: fallbackEpisode?.primaryImageTag
    val thumbTag: String?
        get() =
            series?.thumbImageTag
                ?: series?.seriesThumbImageTag
                ?: fallbackEpisode?.seriesThumbImageTag
                ?: fallbackEpisode?.thumbImageTag
    val backdropTag: String?
        get() =
            series?.backdropImageTag
                ?: series?.seriesBackdropImageTag
                ?: fallbackEpisode?.seriesBackdropImageTag
                ?: fallbackEpisode?.backdropImageTag
    val primaryImageItemId: String?
        get() = series?.id ?: fallbackEpisode?.seriesId ?: fallbackEpisode?.parentId
    val logoTag: String?
        get() = series?.parentLogoImageTag ?: fallbackEpisode?.parentLogoImageTag
    val openItem: JellyfinItem?
        get() = series ?: fallbackEpisode
}

private fun groupTvSeries(items: List<JellyfinItem>): List<TvSeriesGroup> {
    val groups = linkedMapOf<String, MutableTvSeriesGroup>()
    val nameToKey = mutableMapOf<String, String>()

    fun normalize(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun ensureGroup(key: String): MutableTvSeriesGroup = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }

    items
        .filter { it.type.equals("Series", ignoreCase = true) }
        .forEach { series ->
            val key = "series:${series.id}"
            val group = ensureGroup(key)
            group.series = series
            normalize(series.name)?.let { normalized ->
                nameToKey[normalized] = key
            }
        }

    items
        .filter { it.type.equals("Episode", ignoreCase = true) }
        .forEach { episode ->
            val normalizedName = normalize(episode.seriesName)
            val key =
                normalizedName?.let { nameToKey[it] }
                    ?: episode.parentId?.let { "parent:$it" }
                    ?: episode.seasonId?.let { "season:$it" }
                    ?: normalizedName?.let { "series-name:$it" }
                    ?: "episode:${episode.id}"
            val group = ensureGroup(key)
            group.episodes += episode
            if (group.series == null) {
                group.series = episode.toSeriesPlaceholder()
            }
            if (normalizedName != null && nameToKey[normalizedName] == null) {
                nameToKey[normalizedName] = key
            }
        }

    return groups.values
        .map { it.toImmutable() }
        .sortedWith(
            compareBy<TvSeriesGroup> {
                it.series?.sortName?.lowercase()
                    ?: it.title.lowercase()
            }.thenBy { it.series?.name ?: it.title },
        )
}

private fun episodeLabel(item: JellyfinItem): String {
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    val parts = mutableListOf<String>()
    if (season != null || episode != null) {
        val code =
            buildString {
                if (season != null) {
                    append("S")
                    append(season)
                }
                if (episode != null) {
                    if (season != null) {
                        append("E")
                    } else {
                        append("E")
                    }
                    append(episode)
                }
            }.takeIf { it.isNotBlank() }
        if (!code.isNullOrBlank()) {
            parts += code
        }
    }
    val title = item.episodeTitle ?: item.name
    if (!title.isNullOrBlank()) {
        parts += title
    }
    return parts.joinToString(" · ").ifBlank { item.name }
}

private data class MutableTvSeriesGroup(
    val key: String,
    var series: JellyfinItem? = null,
    val episodes: MutableList<JellyfinItem> = mutableListOf(),
) {
    fun toImmutable(): TvSeriesGroup =
        TvSeriesGroup(
            id = series?.id ?: key,
            series = series,
            episodes = episodes.distinctBy { it.id },
        )
}

internal data class SeasonEpisodes(
    val seasonNumber: Int?,
    val label: String,
    val episodes: List<JellyfinItem>,
    val sortKey: Int,
)

internal fun buildSeasonEpisodes(episodes: List<JellyfinItem>): List<SeasonEpisodes> =
    episodes
        .groupBy { it.parentIndexNumber }
        .map { (seasonNumber, episodesInSeason) ->
            val label =
                when {
                    seasonNumber == null -> "Episodes"
                    seasonNumber <= 0 -> "Specials"
                    else -> "Season $seasonNumber"
                }
            val sortedEpisodes =
                episodesInSeason.sortedWith(
                    compareBy<JellyfinItem> { it.parentIndexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.indexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.name },
                )
            SeasonEpisodes(
                seasonNumber = seasonNumber,
                label = label,
                episodes = sortedEpisodes,
                sortKey = seasonNumber ?: Int.MAX_VALUE,
            )
        }.sortedWith(compareBy<SeasonEpisodes> { it.sortKey }.thenBy { it.label })

@Composable
private fun TvSeriesCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    onOpenSeries: (JellyfinItem) -> Unit,
    onOpenEpisode: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(group.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val fallbackCandidates =
                    buildList<ImageCandidate> {
                        val episode = group.fallbackEpisode
                        addCandidate(episode?.id, episode?.primaryImageTag, "Primary")
                        addCandidate(episode?.id, episode?.thumbImageTag, "Thumb")
                        addCandidate(episode?.id, episode?.backdropImageTag, "Backdrop")
                    }
                val seriesItemId = group.primaryImageItemId
                PosterImage(
                    modifier =
                        Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f),
                    baseUrl = baseUrl,
                    itemId = group.posterItemId,
                    primaryTag = group.primaryImageTag,
                    thumbTag = group.thumbTag,
                    backdropTag = group.backdropTag,
                    accessToken = accessToken,
                    contentDescription = group.title,
                    primaryImageItemId = seriesItemId,
                    thumbImageItemId = seriesItemId,
                    backdropImageItemId = seriesItemId,
                    logoImageItemId = seriesItemId,
                    logoTag = group.logoTag,
                    extraCandidates = fallbackCandidates,
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    group.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) 6 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    group.openItem?.let { seriesItem ->
                        TextButton(onClick = { onOpenSeries(seriesItem) }) {
                            Text("View series")
                        }
                    }
                }
            }
            if (group.episodes.isNotEmpty()) {
                if (expanded) {
                    val seasonGroups = buildSeasonEpisodes(group.episodes)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasonGroups.forEach { season ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = season.label,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                season.episodes.forEach { episode ->
                                    TextButton(onClick = { onOpenEpisode(episode) }) {
                                        Text(
                                            text = episodeLabel(episode),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemRow(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosterImage(
                modifier =
                    Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f),
                baseUrl = baseUrl,
                itemId = item.id,
                primaryTag = item.primaryImageTag,
                thumbTag = item.thumbImageTag,
                backdropTag = item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
            )
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.overview.isNullOrBlank()) {
                    Text(
                        text = item.overview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.productionYear?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    item.officialRating?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    val runtimeMinutes = item.runTimeTicks?.let(::ticksToMinutes)
                    if (runtimeMinutes != null && runtimeMinutes > 0) {
                        Text(
                            text = "${runtimeMinutes}m",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UnusedParameter")
private fun PosterImage(
    modifier: Modifier,
    baseUrl: String?,
    itemId: String,
    primaryTag: String?,
    thumbTag: String?,
    backdropTag: String?,
    accessToken: String?,
    contentDescription: String,
    primaryImageItemId: String? = null,
    thumbImageItemId: String? = null,
    backdropImageItemId: String? = null,
    logoImageItemId: String? = null,
    logoTag: String? = null,
    preferLogo: Boolean = false,
    extraCandidates: List<ImageCandidate> = emptyList(),
) {
    val shape = MaterialTheme.shapes.medium
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder =
        remember(contentDescription) {
            contentDescription
                .split(" ")
                .firstOrNull()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
        }
    val imageUrl =
        remember(
            baseUrl,
            accessToken,
            itemId,
            primaryTag,
            thumbTag,
            backdropTag,
            primaryImageItemId,
            thumbImageItemId,
            backdropImageItemId,
            logoImageItemId,
            logoTag,
            preferLogo,
            extraCandidates,
        ) {
            buildList {
                if (preferLogo) {
                    addCandidate(logoImageItemId, logoTag, "Logo")
                }
                addCandidate(primaryImageItemId ?: itemId, primaryTag, "Primary")
                addCandidate(thumbImageItemId ?: itemId, thumbTag, "Thumb")
                addCandidate(backdropImageItemId ?: itemId, backdropTag, "Backdrop")
                addAll(extraCandidates)
                if (!preferLogo) {
                    addCandidate(logoImageItemId, logoTag, "Logo")
                }
            }.firstNotNullOfOrNull { candidate ->
                buildImageUrl(baseUrl, candidate.itemId, candidate.tag, candidate.type, accessToken)
            }
        }
    val context = LocalPlatformContext.current
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(fallbackColor),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
            )
        } else if (!placeholder.isNullOrBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ImageCandidate(
    val itemId: String,
    val tag: String,
    val type: String,
)

private fun MutableList<ImageCandidate>.addCandidate(
    itemId: String?,
    tag: String?,
    type: String,
) {
    if (!itemId.isNullOrBlank() && !tag.isNullOrBlank()) {
        add(ImageCandidate(itemId = itemId, tag = tag, type = type))
    }
}

private fun JellyfinItem.toSeriesPlaceholder(): JellyfinItem {
    val placeholderId = seriesId ?: parentId ?: id
    return copy(
        id = placeholderId,
        type = "Series",
        name = seriesName ?: name,
        sortName = sortName ?: seriesName,
        overview = null,
        taglines = emptyList(),
        primaryImageTag = seriesPrimaryImageTag ?: primaryImageTag,
        thumbImageTag = seriesThumbImageTag ?: thumbImageTag,
        backdropImageTag = seriesBackdropImageTag ?: backdropImageTag,
        seriesId = placeholderId,
        seriesPrimaryImageTag = seriesPrimaryImageTag ?: primaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag ?: thumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag ?: backdropImageTag,
    )
}

private fun buildImageUrl(
    baseUrl: String?,
    itemId: String,
    tag: String?,
    imageType: String,
    accessToken: String?,
): String? {
    if (baseUrl.isNullOrBlank() || tag.isNullOrBlank()) {
        return null
    }
    val normalizedBase =
        if (baseUrl.endsWith("/")) {
            baseUrl.dropLast(1)
        } else {
            baseUrl
        }
    val tokenQuery = accessToken?.let { "&api_key=$it" }.orEmpty()
    return "$normalizedBase/Items/$itemId/Images/$imageType?tag=$tag$tokenQuery"
}

@Composable
private fun StatusBanner(
    state: JellyfinHomeState,
    onRetry: () -> Unit,
    onConnect: () -> Unit,
) {
    when {
        state.errorMessage != null -> {
            val message = state.errorMessage.orEmpty()
            AssistChip(
                onClick = onRetry,
                label = { Text(message) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
        state.selectedLibraryId == null && state.libraries.isNotEmpty() ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Select a library to browse") },
            )
        state.libraries.isEmpty() ->
            AssistChip(
                onClick = onConnect,
                enabled = true,
                label = { Text("Connect a Jellyfin server to start browsing") },
            )
        else -> Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun LoadMoreListener(
    listState: LazyListState,
    shouldLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, shouldLoadMore) {
        if (!shouldLoadMore) {
            return@LaunchedEffect
        }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.filter { index -> index >= 0 }
            .distinctUntilChanged()
            .collect { index ->
                val nearingEnd = index >= listState.layoutInfo.totalItemsCount - 4
                if (nearingEnd) {
                    onLoadMore()
                }
            }
    }
}

@Composable
internal fun JellyfinDetailContent(
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
    seasons: List<SeasonEpisodes>,
    onPlay: () -> Unit,
    onQueueDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        PosterImage(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            baseUrl = baseUrl,
            itemId = detail.id,
            primaryTag = detail.primaryImageTag,
            thumbTag = null,
            backdropTag = detail.backdropImageTags.firstOrNull(),
            accessToken = accessToken,
            contentDescription = detail.name,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPlay) {
                Text(text = "Play")
            }
            OutlinedButton(onClick = onQueueDownload) {
                Text(text = "Download")
            }
        }
        if (detail.taglines.isNotEmpty()) {
            Text(
                text = detail.taglines.joinToString(separator = "\n"),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (!detail.overview.isNullOrBlank()) {
            Text(
                text = detail.overview!!,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (seasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                )
                seasons.forEach { season ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = season.label,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        season.episodes.forEach { episode ->
                            Text(
                                text = episodeLabel(episode),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        if (detail.mediaSources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Media Sources",
                    style = MaterialTheme.typography.titleMedium,
                )
                detail.mediaSources.forEach { source ->
                    Card {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = source.name ?: source.container.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val runtimeMinutes = source.runTimeTicks?.let(::ticksToMinutes)
                            if (runtimeMinutes != null && runtimeMinutes > 0) {
                                Text(
                                    text = "Runtime: ${runtimeMinutes}m",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (source.streams.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    source.streams.forEach { stream ->
                                        Text(
                                            text =
                                                "${stream.type.name.lowercase().replaceFirstChar(Char::uppercase)}: " +
                                                    (stream.displayTitle ?: stream.codec.orEmpty()),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (detail.genres.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Genres",
                    style = MaterialTheme.typography.titleMedium,
                )
                detail.genres.joinToString(separator = ", ").takeIf { it.isNotBlank() }?.let { genres ->
                    Text(
                        text = genres,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (detail.studios.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Studios",
                    style = MaterialTheme.typography.titleMedium,
                )
                detail.studios
                    .joinToString(separator = ", ")
                    .takeIf { it.isNotBlank() }
                    ?.let { studios ->
                        Text(
                            text = studios,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
            }
        }
    }
}

private fun progressFraction(item: JellyfinItem): Float {
    val percentage = item.playedPercentage
    if (percentage != null) {
        return (percentage / 100.0).coerceIn(0.0, 1.0).toFloat()
    }
    val position = item.positionTicks ?: return 0f
    val runtime = item.runTimeTicks ?: return 0f
    if (runtime <= 0) return 0f
    return (position.toDouble() / runtime).coerceIn(0.0, 1.0).toFloat()
}

private fun ticksToMinutes(ticks: Long): Int = (ticks / 600_000_000L).toInt()
