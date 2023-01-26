package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.Author
import ru.netology.coroutines.dto.Comment
import ru.netology.coroutines.dto.Post
import ru.netology.coroutines.dto.PostWithComments
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private const val BASE_URL = "http://127.0.0.1:9999"
private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    runBlocking {
        val posts = okHttpClient.makeRequest("${BASE_URL}/api/slow/posts", object : TypeToken<List<Post>>() {})

        val tasks = posts.map {
            async {
                val authorById =
                    okHttpClient.makeRequest("${BASE_URL}/api/authors/${it.authorId}", object : TypeToken<Author>() {})
                val comments = okHttpClient.makeRequest(
                    "${BASE_URL}/api/slow/posts/${it.id}/comments", object : TypeToken<List<Comment>>() {})
                val comWithAuthors = comments.map{
                    val comAuthorById =
                        okHttpClient.makeRequest("${BASE_URL}/api/authors/${it.authorId}", object : TypeToken<Author>() {})
                    it.copy(author = comAuthorById.name, authorAvatar = comAuthorById.avatar)
                }

                PostWithComments(
                    it.copy(author = authorById.name, authorAvatar = authorById.avatar),
                    comWithAuthors)
            }
        }
        val result = tasks.awaitAll()
        println("Результат :$result")
    }
}


suspend fun <T> OkHttpClient.makeRequest(url: String,  typeToken: TypeToken<T>): T = suspendCoroutine { continuation ->
    Request.Builder()
        .url(url)
        .build()
        .let {
            okHttpClient.newCall(it)
        }.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.let{
                    gson.fromJson<T>(it, typeToken.type)
                }
                if (result == null){
                    continuation.resumeWithException(RuntimeException("Body is null"))
                    return
                }
                continuation.resume(result)
            }
        }
        )

}
