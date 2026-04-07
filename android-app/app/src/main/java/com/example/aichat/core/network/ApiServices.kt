package com.example.aichat.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("v1/auth/google")
    suspend fun exchangeGoogleToken(@Body body: GoogleAuthRequestDto): SessionResponseDto

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshSessionRequestDto): SessionResponseDto

    @POST("v1/auth/logout")
    suspend fun logout()
}

interface ProfileApi {
    @GET("v1/profile/me")
    suspend fun getProfile(): ProfileDto

    @PATCH("v1/profile/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): ProfileDto
}

interface CharacterApi {
    @POST("v1/characters")
    suspend fun createCharacter(@Body body: CharacterWriteRequestDto): CharacterDto

    @PATCH("v1/characters/{characterId}")
    suspend fun updateCharacter(
        @Path("characterId") characterId: String,
        @Body body: CharacterWriteRequestDto
    ): CharacterDto

    @GET("v1/characters/{characterId}")
    suspend fun getCharacter(@Path("characterId") characterId: String): CharacterDto

    @GET("v1/characters/me")
    suspend fun getOwnedCharacters(@Query("cursor") cursor: String? = null): CursorPageDto<CharacterDto>

    @GET("v1/characters/me/liked")
    suspend fun getLikedCharacters(@Query("cursor") cursor: String? = null): CursorPageDto<CharacterDto>

    @POST("v1/characters/{characterId}/like")
    suspend fun likeCharacter(@Path("characterId") characterId: String)

    @DELETE("v1/characters/{characterId}/like")
    suspend fun unlikeCharacter(@Path("characterId") characterId: String)
}

interface HomeApi {
    @GET("v1/home/feed")
    suspend fun getFeed(@Query("cursor") cursor: String? = null): CursorPageDto<CharacterDto>

    @GET("v1/home/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("cursor") cursor: String? = null
    ): CursorPageDto<CharacterDto>
}

interface ConversationApi {
    @GET("v1/conversations")
    suspend fun getConversations(@Query("cursor") cursor: String? = null): CursorPageDto<ConversationSummaryDto>

    @POST("v1/conversations")
    suspend fun createConversation(@Body body: Map<String, String>): ConversationSummaryDto

    @GET("v1/conversations/{conversationId}")
    suspend fun getConversation(@Path("conversationId") conversationId: String): ConversationDetailDto
    @POST("v1/conversations/{conversationId}/read")
    suspend fun markConversationRead(@Path("conversationId") conversationId: String)
}

interface ChatApi {
    @PATCH("v1/messages/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: String,
        @Body body: EditMessageRequestDto
    )

    @POST("v1/messages/{messageId}/rewind")
    suspend fun rewind(@Path("messageId") messageId: String)

    @POST("v1/messages/{messageId}/select-regeneration")
    suspend fun selectRegeneration(
        @Path("messageId") messageId: String,
        @Body body: SelectRegenerationRequestDto
    )
}

interface ImageApi {
    @POST("v1/images/generate-character-portrait")
    suspend fun generatePortrait(@Body body: GeneratePortraitRequestDto): GeneratePortraitResponseDto
}

