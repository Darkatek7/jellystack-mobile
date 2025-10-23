@file:Suppress("ktlint:standard:function-naming")

package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfileOption
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMessageKind
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.tmdb.tmdbPosterUrl

@Composable
fun JellyseerrRequestsScreen(
    state: JellyseerrRequestsState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
    onCreateRequest: (JellyseerrSearchItem, JellyseerrLanguageProfileOption?) -> Unit,
    onDeleteRequest: (JellyseerrRequestSummary) -> Unit,
    onRemoveMedia: (JellyseerrRequestSummary) -> Unit,
    onMessageAcknowledged: () -> Unit,
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    if (state is JellyseerrRequestsState.Ready) {
        state.message?.let { message ->
            LaunchedEffect(message) {
                val actionLabel = if (message.kind == JellyseerrMessageKind.ERROR) "Dismiss" else null
                snackbarHostState.showSnackbar(
                    message = message.text,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Short,
                )
                onMessageAcknowledged()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            JellyseerrRequestsState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            JellyseerrRequestsState.MissingServer ->
                MissingServerPlaceholder(
                    onAddServer = onAddServer,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                )

            is JellyseerrRequestsState.Error ->
                ErrorPlaceholder(
                    message = state.message,
                    onRetry = onRefresh,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                )

            is JellyseerrRequestsState.Ready ->
                RequestsContent(
                    state = state,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                    onSelectFilter = onSelectFilter,
                    onRefresh = onRefresh,
                    onCreateRequest = onCreateRequest,
                    onDeleteRequest = onDeleteRequest,
                    onRemoveMedia = onRemoveMedia,
                    modifier = Modifier.fillMaxSize(),
                )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
        )
    }
}

@Composable
private fun RequestsContent(
    state: JellyseerrRequestsState.Ready,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
    onCreateRequest: (JellyseerrSearchItem, JellyseerrLanguageProfileOption?) -> Unit,
    onDeleteRequest: (JellyseerrRequestSummary) -> Unit,
    onRemoveMedia: (JellyseerrRequestSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Jellyseerr Requests",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            SearchBar(
                query = state.query,
                isSearching = state.isSearching,
                onQueryChanged = onSearch,
                onClear = onClearSearch,
            )
        }
        if (state.searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "Search results",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(state.searchResults, key = { it.tmdbId }) { result ->
                val availableProfiles =
                    when (result.mediaType) {
                        JellyseerrMediaType.MOVIE -> state.languageProfiles.movies
                        JellyseerrMediaType.TV -> state.languageProfiles.tv
                        else -> emptyList()
                    }
                SearchResultCard(
                    item = result,
                    languageProfiles = availableProfiles,
                    onCreateRequest = onCreateRequest,
                )
            }
            item { Divider() }
        } else if (state.query.isNotBlank()) {
            item {
                Text(
                    text = "No results for “${state.query}”.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Divider() }
        }

        item {
            FilterRow(
                selected = state.filter,
                isRefreshing = state.isRefreshing,
                onSelectFilter = onSelectFilter,
                onRefresh = onRefresh,
            )
        }

        if (state.requests.isEmpty()) {
            item {
                EmptyRequestsPlaceholder()
            }
        } else {
            items(state.requests, key = { it.id }) { summary ->
                RequestCard(
                    summary = summary,
                    isAdmin = state.isAdmin,
                    onDelete = onDeleteRequest,
                    onRemoveMedia = onRemoveMedia,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search movies and series") },
            singleLine = true,
            trailingIcon =
                if (query.isNotBlank()) {
                    {
                        TextButton(onClick = onClear) {
                            Text("Clear")
                        }
                    }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isSearching) {
            LinearLoadingIndicator()
        }
    }
}

@Composable
private fun FilterRow(
    selected: JellyseerrRequestFilter,
    isRefreshing: Boolean,
    onSelectFilter: (JellyseerrRequestFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Requests",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh requests")
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JellyseerrRequestFilter.values().forEach { filter ->
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.label()) },
                )
            }
        }
        Divider()
    }
}

@Composable
private fun SearchResultCard(
    item: JellyseerrSearchItem,
    languageProfiles: List<JellyseerrLanguageProfileOption>,
    onCreateRequest: (JellyseerrSearchItem, JellyseerrLanguageProfileOption?) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val posterPlaceholder =
                remember(item.title) {
                    item.title
                        .firstOrNull { it.isLetterOrDigit() }
                        ?.uppercaseChar()
                        ?.toString()
                }
            val resolvedPosterPath =
                remember(item.posterPath, item.backdropPath) {
                    item.posterPath ?: item.backdropPath
                }
            PosterArtwork(
                posterPath = resolvedPosterPath,
                contentDescription = item.title,
                placeholderText = posterPlaceholder,
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        buildString {
                            append(item.mediaType.displayName())
                            item.releaseYear?.let {
                                append(" • ")
                                append(it)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val overviewText = item.overview
                if (!overviewText.isNullOrBlank()) {
                    Text(
                        text = overviewText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.requests.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.requests.forEach { request ->
                            AssistChip(
                                onClick = {},
                                label = { Text(request.requestStatus.label()) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                } else {
                    val hasProfiles = languageProfiles.isNotEmpty()
                    var isMenuOpen by remember(item.tmdbId) { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { isMenuOpen = true },
                            enabled = hasProfiles,
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Request")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false },
                        ) {
                            languageProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.displayLabel()) },
                                    onClick = {
                                        isMenuOpen = false
                                        onCreateRequest(item, profile)
                                    },
                                )
                            }
                        }
                        if (!hasProfiles) {
                            Text(
                                text = "No language profiles available.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun JellyseerrLanguageProfileOption.displayLabel(): String {
    val parts = mutableListOf<String>()
    val service = serviceName?.takeIf { it.isNotBlank() }
    val profileName = name.takeIf { it.isNotBlank() }
    if (service != null) {
        parts += service
    }
    if (profileName != null && profileName != service) {
        parts += profileName
    } else if (service == null && profileName != null) {
        parts += profileName
    }
    if (is4k) {
        parts += "4K"
    }
    if (isDefault) {
        parts += "Default"
    }
    if (parts.isEmpty()) {
        languageProfileId?.let { parts += "Profile $it" }
    }
    return parts.filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Request" }
}

@Composable
private fun RequestCard(
    summary: JellyseerrRequestSummary,
    isAdmin: Boolean,
    onDelete: (JellyseerrRequestSummary) -> Unit,
    onRemoveMedia: (JellyseerrRequestSummary) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val resolvedTitle =
                remember(summary.title, summary.originalTitle, summary.mediaType) {
                    summary.title?.takeUnless { it.isBlank() }
                        ?: summary.originalTitle?.takeUnless { it.isBlank() }
                        ?: summary.mediaType.displayName()
                }
            val posterPlaceholder =
                remember(resolvedTitle) {
                    resolvedTitle.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                }
            val resolvedPosterPath =
                remember(summary.posterPath, summary.backdropPath) {
                    summary.posterPath ?: summary.backdropPath
                }
            PosterArtwork(
                posterPath = resolvedPosterPath,
                contentDescription = resolvedTitle,
                placeholderText = posterPlaceholder,
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = summary.mediaType.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(status = summary.requestStatus)
                }
                if (summary.availability.standard != JellyseerrMediaStatus.UNKNOWN) {
                    AvailabilityBadge(summary)
                }
                summary.requestedBy?.displayName?.let { requester ->
                    Text(
                        text = "Requested by $requester",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (summary.seasons.isNotEmpty()) {
                    Text(
                        text =
                            "Seasons: ${
                                summary.seasons.joinToString {
                                    "${it.seasonNumber} (${it.status.label()})"
                                }
                            }",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { onDelete(summary) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete request",
                        )
                    }
                    if (isAdmin && summary.canRemoveFromService) {
                        Button(
                            onClick = { onRemoveMedia(summary) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Remove media")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterArtwork(
    posterPath: String?,
    contentDescription: String,
    placeholderText: String?,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(backgroundColor) { ColorPainter(backgroundColor) }
    val context = LocalPlatformContext.current
    val imageUrl = remember(posterPath) { tmdbPosterUrl(posterPath) }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(backgroundColor),
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
                placeholder = placeholderPainter,
                error = placeholderPainter,
            )
        } else if (!placeholderText.isNullOrBlank()) {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: JellyseerrRequestStatus) {
    val colors =
        when (status) {
            JellyseerrRequestStatus.APPROVED,
            JellyseerrRequestStatus.COMPLETED,
            -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            JellyseerrRequestStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            JellyseerrRequestStatus.DECLINED,
            JellyseerrRequestStatus.FAILED,
            -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    AssistChip(
        onClick = {},
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = colors.first,
                labelColor = colors.second,
            ),
        label = { Text(status.label()) },
    )
}

@Composable
private fun AvailabilityBadge(summary: JellyseerrRequestSummary) {
    val available =
        when (summary.availability.standard) {
            JellyseerrMediaStatus.AVAILABLE -> "Available"
            JellyseerrMediaStatus.PROCESSING -> "Processing"
            JellyseerrMediaStatus.PENDING -> "Pending"
            JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> "Partial"
            JellyseerrMediaStatus.BLACKLISTED -> "Blacklisted"
            JellyseerrMediaStatus.DELETED -> "Deleted"
            else -> "Unknown"
        }
    AssistChip(
        onClick = {},
        label = {
            Text(available)
        },
    )
}

@Composable
private fun EmptyRequestsPlaceholder() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No requests yet.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Search for a title to create your first Jellyseerr request.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MissingServerPlaceholder(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connect Jellyseerr to get started",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a Jellyseerr server in settings to browse and manage requests.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddServer) {
            Text("Manage servers")
        }
    }
}

@Composable
private fun ErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
private fun LinearLoadingIndicator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .height(16.dp)
                    .width(16.dp),
            strokeWidth = 2.dp,
        )
    }
}

private fun JellyseerrRequestStatus.label(): String =
    when (this) {
        JellyseerrRequestStatus.PENDING -> "Pending"
        JellyseerrRequestStatus.APPROVED -> "Approved"
        JellyseerrRequestStatus.DECLINED -> "Declined"
        JellyseerrRequestStatus.FAILED -> "Failed"
        JellyseerrRequestStatus.COMPLETED -> "Completed"
        JellyseerrRequestStatus.UNKNOWN -> "Unknown"
    }

private fun JellyseerrRequestFilter.label(): String =
    when (this) {
        JellyseerrRequestFilter.ALL -> "All"
        JellyseerrRequestFilter.PENDING -> "Pending"
        JellyseerrRequestFilter.APPROVED -> "Approved"
        JellyseerrRequestFilter.PROCESSING -> "Processing"
        JellyseerrRequestFilter.AVAILABLE -> "Available"
        JellyseerrRequestFilter.UNAVAILABLE -> "Unavailable"
        JellyseerrRequestFilter.FAILED -> "Failed"
        JellyseerrRequestFilter.DELETED -> "Deleted"
    }

private fun JellyseerrMediaType.displayName(): String =
    when (this) {
        JellyseerrMediaType.MOVIE -> "Movie"
        JellyseerrMediaType.TV -> "Series"
        JellyseerrMediaType.PERSON -> "Person"
        JellyseerrMediaType.COLLECTION -> "Collection"
        JellyseerrMediaType.UNKNOWN -> "Unknown"
    }
