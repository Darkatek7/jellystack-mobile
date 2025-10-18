@file:Suppress("FunctionName")

package dev.jellystack.design.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import dev.jellystack.core.downloads.DownloadStatus
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleTrack
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

@Composable
fun JellyfinBrowseScreen(
    state: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (JellyfinItem) -> Unit,
    onConnectServer: () -> Unit,
    showLibrarySelector: Boolean = true,
    showLibraryItems: Boolean = true,
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
    val nextUpItems =
        remember(state.continueWatching, state.libraryItems) {
            val continueEpisodes = state.continueWatching.filter { it.type.equals("Episode", ignoreCase = true) }
            val upcomingEpisodes =
                state.libraryItems
                    .filter { it.type.equals("Episode", ignoreCase = true) }
                    .filter { (it.playedPercentage ?: 0.0) < 90.0 }
            (continueEpisodes + upcomingEpisodes)
                .distinctBy { it.id }
                .take(12)
        }
    val recentShowGroups =
        remember(state.recentShows) {
            if (state.recentShows.isEmpty()) {
                emptyList()
            } else {
                val groups = linkedMapOf<String, MutableTvSeriesGroup>()
                state.recentShows.forEach { item ->
                    when {
                        item.type.equals("Series", ignoreCase = true) -> {
                            val key = "series:${item.id}"
                            val group = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }
                            group.series = item
                        }
                        item.type.equals("Episode", ignoreCase = true) -> {
                            val key =
                                item.seriesId?.let { seriesId -> "series:$seriesId" }
                                    ?: item.parentId?.let { parentId -> "parent:$parentId" }
                                    ?: "episode:${item.id}"
                            val group = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }
                            group.episodes += item
                            if (group.series == null) {
                                group.series = item.toSeriesPlaceholder()
                            }
                        }
                    }
                }
                groups.values.map { it.toImmutable() }.take(12)
            }
        }
    val recentMovieItems =
        remember(state.recentMovies) {
            state.recentMovies
                .asSequence()
                .filter { item ->
                    item.type.equals("Movie", ignoreCase = true) ||
                        item.mediaType.equals("Video", ignoreCase = true)
                }.distinctBy { it.id }
                .take(12)
                .toList()
        }
    if (showLibraryItems) {
        LoadMoreListener(
            listState = listState,
            shouldLoadMore = !state.endReached && !state.isPageLoading && !state.isInitialLoading && state.libraryItems.isNotEmpty(),
            onLoadMore = onLoadMore,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "status") {
                StatusBanner(
                    state = state,
                    onRetry = onRefresh,
                    onConnect = onConnectServer,
                )
            }
            if (showLibrarySelector && state.libraries.isNotEmpty()) {
                item(key = "libraries") {
                    LibrarySelector(
                        libraries = state.libraries,
                        selectedLibraryId = state.selectedLibraryId,
                        onSelectLibrary = onSelectLibrary,
                    )
                }
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
            if (nextUpItems.isNotEmpty()) {
                item(key = "nextUp") {
                    NextUpSection(
                        items = nextUpItems,
                        baseUrl = state.imageBaseUrl,
                        accessToken = state.imageAccessToken,
                        onOpenItem = onOpenDetail,
                    )
                }
            }
            item(key = "recentShows") {
                RecentlyAddedShowsSection(
                    groups = recentShowGroups,
                    baseUrl = state.imageBaseUrl,
                    accessToken = state.imageAccessToken,
                    onOpenItem = onOpenDetail,
                )
            }
            item(key = "recentMovies") {
                RecentlyAddedMoviesSection(
                    items = recentMovieItems,
                    baseUrl = state.imageBaseUrl,
                    accessToken = state.imageAccessToken,
                    onOpenItem = onOpenDetail,
                )
            }
            if (showLibraryItems) {
                item(key = "spacerAfterRecent") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (isTvLibrary) {
                    if (seriesGroups.isNotEmpty()) {
                        item(key = "allSeriesHeader") {
                            SectionHeader(title = "All Series")
                        }
                    }
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
                        )
                    }
                } else {
                    if (state.libraryItems.isNotEmpty()) {
                        item(key = "allItemsHeader") {
                            SectionHeader(title = "All Items")
                        }
                    }
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
            }
            if (showLibraryItems && state.isPageLoading) {
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
        val isEpisode = item.type.equals("Episode", ignoreCase = true)
        val primaryText =
            when {
                isEpisode -> item.seriesName ?: item.name
                else -> item.name
            }
        val secondaryText =
            if (isEpisode) {
                formatEpisodeLabel(
                    seasonNumber = item.parentIndexNumber,
                    episodeNumber = item.indexNumber,
                )
            } else {
                item.officialRating?.takeIf { it.isNotBlank() }
            }
        val tertiaryText =
            if (isEpisode) {
                item.episodeTitle?.takeIf { it.isNotBlank() } ?: item.name
            } else {
                item.productionYear?.takeIf { it > 0 }?.toString()
            }
        MediaCardMetadata(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
        )
    }
}

private fun formatEpisodeLabel(
    seasonNumber: Int?,
    episodeNumber: Int?,
): String? {
    val hasSeason = seasonNumber != null && seasonNumber > 0
    val hasEpisode = episodeNumber != null && episodeNumber > 0
    return when {
        hasSeason && hasEpisode -> "S$seasonNumber • E$episodeNumber"
        hasEpisode -> "Episode $episodeNumber"
        hasSeason -> "Season $seasonNumber"
        else -> null
    }
}

@Composable
private fun NextUpSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Next Up",
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
        if (items.isEmpty()) {
            EmptySectionMessage("No upcoming episodes yet")
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                NextUpCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

@Composable
private fun NextUpCard(
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
        PosterImage(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            baseUrl = baseUrl,
            itemId = item.parentId ?: item.seriesId ?: item.id,
            primaryTag = item.primaryImageTag ?: item.seriesPrimaryImageTag,
            thumbTag = item.thumbImageTag ?: item.seriesThumbImageTag,
            backdropTag = item.backdropImageTag ?: item.seriesBackdropImageTag,
            accessToken = accessToken,
            contentDescription = item.name,
            primaryImageItemId = item.parentId ?: item.seriesId,
            thumbImageItemId = item.parentId ?: item.seriesId,
            backdropImageItemId = item.parentId ?: item.seriesId,
            logoImageItemId = item.parentId ?: item.seriesId,
            logoTag = item.parentLogoImageTag,
        )
        val isEpisode = item.type.equals("Episode", ignoreCase = true)
        val primaryText =
            when {
                isEpisode -> item.seriesName ?: item.name
                else -> item.name
            }
        val secondaryText =
            if (isEpisode) {
                formatEpisodeLabel(
                    seasonNumber = item.parentIndexNumber,
                    episodeNumber = item.indexNumber,
                )
            } else {
                item.officialRating?.takeIf { it.isNotBlank() }
            }
        val tertiaryText =
            if (isEpisode) {
                item.episodeTitle?.takeIf { it.isNotBlank() } ?: item.name
            } else {
                item.productionYear?.takeIf { it > 0 }?.toString()
            }
        MediaCardMetadata(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
        )
    }
}

@Composable
private fun MediaCardMetadata(
    primaryText: String,
    secondaryText: String?,
    tertiaryText: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tertiaryText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tertiaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f, fill = true))
        }
    }
}

@Composable
private fun RecentlyAddedShowsSection(
    groups: List<TvSeriesGroup>,
    baseUrl: String?,
    accessToken: String?,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Recently Added Shows")
        Spacer(modifier = Modifier.height(12.dp))
        if (groups.isEmpty()) {
            EmptySectionMessage("No shows available yet")
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.id }) { group ->
                SeriesPosterCard(
                    group = group,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    onOpenSeries = onOpenItem,
                )
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    onOpenSeries: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetItem = group.openItem
    val extraCandidates =
        remember(group) {
            buildList<ImageCandidate> {
                group.episodes.forEach { episode ->
                    addCandidate(episode.id, episode.primaryImageTag, "Primary")
                    addCandidate(episode.id, episode.thumbImageTag, "Thumb")
                    addCandidate(episode.id, episode.backdropImageTag, "Backdrop")
                }
            }
        }
    Card(
        modifier = modifier.width(148.dp),
        onClick = { targetItem?.let(onOpenSeries) },
        enabled = targetItem != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PosterImage(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            baseUrl = baseUrl,
            itemId = group.posterItemId,
            primaryTag = group.primaryImageTag,
            thumbTag = group.thumbTag,
            backdropTag = group.backdropTag,
            accessToken = accessToken,
            contentDescription = group.title,
            primaryImageItemId = group.primaryImageItemId,
            thumbImageItemId = group.primaryImageItemId,
            backdropImageItemId = group.primaryImageItemId,
            logoImageItemId = group.primaryImageItemId,
            logoTag = group.logoTag,
            extraCandidates = extraCandidates,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val overview = group.overview?.takeIf { it.isNotBlank() } ?: " "
            Text(
                text = overview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentlyAddedMoviesSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "Recently Added Movies")
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptySectionMessage("No movies available yet")
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                MoviePosterCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

@Composable
private fun MoviePosterCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(148.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PosterImage(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                listOfNotNull(
                    item.productionYear?.toString(),
                    item.officialRating,
                ).joinToString(" • ")
            val detailsText = details.takeIf { it.isNotBlank() } ?: " "
            Text(
                text = detailsText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun EmptySectionMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

private fun audioTrackLabel(track: AudioTrack): String =
    track.title?.takeIf { it.isNotBlank() }
        ?: track.language?.takeIf { it.isNotBlank() }?.uppercase()
        ?: track.codec?.uppercase()
        ?: track.id

private fun subtitleTrackLabel(track: SubtitleTrack): String =
    track.title?.takeIf { it.isNotBlank() }
        ?: track.language?.takeIf { it.isNotBlank() }?.uppercase()
        ?: track.format.name

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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            group.openItem?.let(onOpenSeries)
        },
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
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
@OptIn(ExperimentalLayoutApi::class)
internal fun JellyfinDetailContent(
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
    seasons: List<SeasonEpisodes>,
    onPlay: () -> Unit,
    downloadStatus: DownloadStatus? = null,
    onQueueDownload: () -> Unit,
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    audioTracks: List<AudioTrack> = emptyList(),
    selectedAudioTrack: AudioTrack? = null,
    onSelectAudioTrack: (AudioTrack) -> Unit = {},
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleTrack: SubtitleTrack? = null,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
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
        var downloadLabel = "Download"
        var downloadEnabled = true
        var primaryAction: () -> Unit = onQueueDownload
        var secondaryLabel: String? = null
        var secondaryAction: (() -> Unit)? = null
        var progressFraction: Float? = null
        var statusMessage: String? = null
        var statusColor = MaterialTheme.colorScheme.onSurfaceVariant

        when (downloadStatus) {
            null -> Unit
            is DownloadStatus.Failed -> {
                downloadLabel = "Retry download"
                statusMessage = downloadStatus.cause.message ?: "Download failed"
                statusColor = MaterialTheme.colorScheme.error
                secondaryLabel = "Clear"
                secondaryAction = onRemoveDownload
            }
            is DownloadStatus.Queued -> {
                downloadLabel = "Queued…"
                downloadEnabled = false
                statusMessage = "Waiting for download slot"
                secondaryLabel = "Cancel"
                secondaryAction = onRemoveDownload
            }
            is DownloadStatus.InProgress -> {
                val total = downloadStatus.totalBytes
                progressFraction =
                    if (total != null && total > 0) {
                        (downloadStatus.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                downloadLabel = "Pause"
                primaryAction = onPauseDownload
                secondaryLabel = "Cancel"
                secondaryAction = onRemoveDownload
                statusMessage =
                    if (total != null && total > 0) {
                        "${formatBytes(downloadStatus.bytesDownloaded)} / ${formatBytes(total)}"
                    } else {
                        "${formatBytes(downloadStatus.bytesDownloaded)} downloaded"
                    }
            }
            is DownloadStatus.Paused -> {
                downloadLabel = "Resume"
                primaryAction = onResumeDownload
                secondaryLabel = "Remove"
                secondaryAction = onRemoveDownload
                statusMessage = "Paused at ${formatBytes(downloadStatus.bytesDownloaded)}"
            }
            is DownloadStatus.Completed -> {
                downloadLabel = "Offline ready"
                downloadEnabled = false
                secondaryLabel = "Remove"
                secondaryAction = onRemoveDownload
                statusMessage = "Stored (${formatBytes(downloadStatus.bytesDownloaded)})"
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPlay) {
                Text(text = "Play")
            }
            OutlinedButton(
                onClick = primaryAction,
                enabled = downloadEnabled,
            ) {
                Text(text = downloadLabel)
            }
            secondaryLabel?.let { label ->
                TextButton(onClick = { secondaryAction?.invoke() }) {
                    Text(text = label)
                }
            }
        }
        progressFraction?.let { progress ->
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )
        }
        if (audioTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Audio tracks",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    audioTracks.forEach { track ->
                        FilterChip(
                            selected = selectedAudioTrack?.id == track.id,
                            onClick = { onSelectAudioTrack(track) },
                            label = { Text(audioTrackLabel(track)) },
                        )
                    }
                }
            }
        }
        if (subtitleTracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedSubtitleTrack == null,
                        onClick = { onSelectSubtitleTrack(null) },
                        label = { Text("Off") },
                    )
                    subtitleTracks.forEach { track ->
                        FilterChip(
                            selected = selectedSubtitleTrack?.id == track.id,
                            onClick = { onSelectSubtitleTrack(track) },
                            label = { Text(subtitleTrackLabel(track)) },
                        )
                    }
                }
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    val mb = kb / 1024.0
    if (mb < 1.0) {
        val rounded = (kb * 10).roundToInt() / 10.0
        return "$rounded KB"
    }
    val gb = mb / 1024.0
    if (gb < 1.0) {
        val rounded = (mb * 10).roundToInt() / 10.0
        return "$rounded MB"
    }
    val rounded = (gb * 10).roundToInt() / 10.0
    return "$rounded GB"
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
