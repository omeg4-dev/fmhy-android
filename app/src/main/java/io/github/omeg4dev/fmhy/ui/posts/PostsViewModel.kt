package io.github.omeg4dev.fmhy.ui.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

data class PostsUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val posts: List<PostItem> = emptyList(),
    val readPaths: Set<String> = emptySet(),
    val error: String? = null,
)

class PostsViewModel(
    private val container: AppContainer,
    private val client: OkHttpClient = container.okHttpClient,
    private val cacheDir: File = container.context.filesDir,
    private val docFetcher: DocFetcher = container.docFetcher,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostsUiState())
    val uiState: StateFlow<PostsUiState> = _uiState.asStateFlow()

    private val cacheFile = File(cacheDir, PostsListing.CACHE_FILE_NAME)

    init {
        loadPosts()
    }

    fun refresh() {
        loadPosts(force = true)
    }

    fun markRead(path: String) {
        viewModelScope.launch {
            PostsReadState.markRead(container.userStore, path)
            _uiState.update { it.copy(readPaths = it.readPaths + path) }
        }
    }

    fun loadPosts(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val now = clock()

            // 1. Try reading cache
            val cachedData = withContext(Dispatchers.IO) {
                PostsListing.readCache(cacheFile)
            }

            if (!force && cachedData != null && (now - cachedData.fetchedAt < 86400L)) {
                val resolvedPosts = withContext(Dispatchers.IO) {
                    cachedData.posts.map { cached ->
                        val fileName = cached.path.substringAfterLast('/')
                        PostsListing.resolvePostItem(fileName, cacheDir)
                    }
                }
                val readPaths = try {
                    PostsReadState.initializeIfFirstRun(container.userStore, resolvedPosts.map { it.path })
                } catch (_: Exception) {
                    emptySet()
                }
                _uiState.value = PostsUiState(
                    isLoading = false,
                    isOffline = false,
                    posts = resolvedPosts,
                    readPaths = readPaths,
                )
                return@launch
            }

            // 2. Fetch listing from jsDelivr
            try {
                val postFileNames = withContext(Dispatchers.IO) {
                    PostsListing.fetchListingFromNetwork(client)
                }

                val resolvedPosts = withContext(Dispatchers.IO) {
                    postFileNames.map { fileName ->
                        PostsListing.resolvePostItem(fileName, cacheDir)
                    }.sortedByDescending { it.dateEpoch }
                }

                withContext(Dispatchers.IO) {
                    val toCache = PostsListingData(
                        fetchedAt = now,
                        posts = resolvedPosts.map {
                            CachedPost(it.path, it.title, it.dateText, it.dateEpoch)
                        },
                    )
                    PostsListing.writeCache(cacheFile, toCache)
                }

                val readPaths = try {
                    PostsReadState.initializeIfFirstRun(container.userStore, resolvedPosts.map { it.path })
                } catch (_: Exception) {
                    emptySet()
                }

                _uiState.value = PostsUiState(
                    isLoading = false,
                    isOffline = false,
                    posts = resolvedPosts,
                    readPaths = readPaths,
                )
            } catch (e: Exception) {
                // Network failed
                if (cachedData != null) {
                    val resolvedPosts = withContext(Dispatchers.IO) {
                        cachedData.posts.map { cached ->
                            val fileName = cached.path.substringAfterLast('/')
                            PostsListing.resolvePostItem(fileName, cacheDir)
                        }
                    }
                    val readPaths = try {
                        PostsReadState.initializeIfFirstRun(container.userStore, resolvedPosts.map { it.path })
                    } catch (_: Exception) {
                        emptySet()
                    }
                    _uiState.value = PostsUiState(
                        isLoading = false,
                        isOffline = true,
                        posts = resolvedPosts,
                        readPaths = readPaths,
                    )
                } else {
                    _uiState.value = PostsUiState(
                        isLoading = false,
                        isOffline = true,
                        error = "Could not load posts. Please check your internet connection.",
                    )
                }
            }
        }
    }

    companion object {
        fun parseDate(fileName: String, frontMatterDate: String?): Long =
            PostsListing.parseDate(fileName, frontMatterDate)

        fun formatDefaultTitle(fileName: String): String =
            PostsListing.formatDefaultTitle(fileName)

        fun formatDateText(fileName: String, frontMatterDate: String?, dateEpoch: Long): String =
            PostsListing.formatDateText(fileName, frontMatterDate, dateEpoch)
    }
}
