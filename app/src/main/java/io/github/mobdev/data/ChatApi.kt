package io.github.mobdev.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the parts of the faerytea.name chat API the app uses.
 *
 * Only public channels are implemented; direct messages (`/inbox`) are out of
 * scope per the assignment.
 */
interface ChatApi {

    /** Token is returned in the `X-Auth-Token` response header. */
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<ResponseBody>

    @GET("channels")
    suspend fun channels(): List<String>

    /**
     * Messages of a channel. With [reverse] = true the server returns up to
     * [limit] messages whose id is *less than* [lastKnownId], newest first,
     * which is what pagination of older messages needs.
     */
    @GET("channel/{name}")
    suspend fun channelMessages(
        @Path("name") name: String,
        @Query("limit") limit: Int = 20,
        @Query("lastKnownId") lastKnownId: Long = 0L,
        @Query("reverse") reverse: Boolean = false,
    ): List<Message>

    @POST("messages")
    suspend fun sendMessage(@Body message: OutgoingMessage): Response<ResponseBody>
}
