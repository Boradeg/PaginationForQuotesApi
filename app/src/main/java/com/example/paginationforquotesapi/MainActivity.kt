package com.example.paginationforquotesapi
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.paginationforquotesapi.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class QuoteItem(
    val author: String,
    val content: String,
    val tags: List<String>
)

data class QuotesData(
    val count: Int,
    val lastItemIndex: Int,
    val page: Int,
    val results: List<Result>,
    val totalCount: Int,
    val totalPages: Int
)

data class Result(
    val _id: String,
    val author: String,
    val authorSlug: String,
    val content: String,
    val dateAdded: String,
    val dateModified: String,
    val length: Int,
    val tags: List<String>
)

interface QuotableService {
    @GET("/quotes")
    suspend fun getQuotes(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): QuotesData
}

class QuotesPagingSource(private val quotableService: QuotableService) : PagingSource<Int, QuoteItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, QuoteItem> {
        return try {
            val page = params.key ?: 1
            val quotesData = quotableService.getQuotes(page, params.loadSize)

            val quotes = quotesData.results?.map {
                QuoteItem(it.author, it.content, it.tags)
            } ?: emptyList()

            LoadResult.Page(
                data = quotes,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (quotes.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, QuoteItem>): Int? {
        return state.anchorPosition
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://api.quotable.io/"

    val instance: QuotableService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(QuotableService::class.java)
    }
}

class QuotesViewModel : ViewModel() {
    private val quotableService = RetrofitClient.instance

    val quotes: Flow<PagingData<QuoteItem>> = Pager(
        config = PagingConfig(pageSize = 10),
        pagingSourceFactory = { QuotesPagingSource(quotableService) }
    ).flow.cachedIn(viewModelScope)
}

class MainActivity : AppCompatActivity() {

    private val viewModel by lazy { ViewModelProvider(this).get(QuotesViewModel::class.java) }
    private lateinit var quotesAdapter: QuotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        quotesAdapter = QuotesAdapter()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = quotesAdapter


        lifecycleScope.launch {
            viewModel.quotes.collect { pagingData ->
                quotesAdapter.submitData(pagingData)
            }
        }
    }
}

class QuotesAdapter : PagingDataAdapter<QuoteItem, QuotesAdapter.QuoteViewHolder>(QuoteItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return QuoteViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: QuoteViewHolder, position: Int) {
        val currentQuote = getItem(position)
        currentQuote?.let {
            holder.bind(it)
        }
    }

    class QuoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorTextView: TextView = itemView.findViewById(R.id.textViewAuthor)
        private val contentTextView: TextView = itemView.findViewById(R.id.textViewContent)
        private val tagsTextView: TextView = itemView.findViewById(R.id.textViewTags)

        fun bind(quoteItem: QuoteItem) {
            authorTextView.text = quoteItem.author
            contentTextView.text = quoteItem.content
            tagsTextView.text = quoteItem.tags.joinToString(", ")
        }
    }

    private class QuoteItemDiffCallback : DiffUtil.ItemCallback<QuoteItem>() {
        override fun areItemsTheSame(oldItem: QuoteItem, newItem: QuoteItem): Boolean {
            return oldItem.author == newItem.author && oldItem.content == newItem.content
        }

        override fun areContentsTheSame(oldItem: QuoteItem, newItem: QuoteItem): Boolean {
            return oldItem == newItem
        }
    }
}
