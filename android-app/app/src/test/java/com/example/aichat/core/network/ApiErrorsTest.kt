package com.example.aichat.core.network

import com.google.common.truth.Truth.assertThat
import java.net.SocketTimeoutException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorsTest {
    @Test
    fun `uses structured backend error instead of generic HTTP message`() {
        val body = """{"code":"VALIDATION_ERROR","message":"Greeting is too long."}"""
            .toResponseBody("application/json".toMediaType())
        val error = HttpException(Response.error<Unit>(400, body))

        assertThat(error.userFacingMessage("Failed to save character."))
            .isEqualTo("Greeting is too long.")
    }

    @Test
    fun `maps server errors when no structured body exists`() {
        val body = "".toResponseBody("application/json".toMediaType())
        val error = HttpException(Response.error<Unit>(503, body))

        assertThat(error.userFacingMessage("Message failed."))
            .isEqualTo("The service is temporarily unavailable. Please try again.")
    }

    @Test
    fun `maps nested timeout errors`() {
        val error = IllegalStateException("request failed", SocketTimeoutException())

        assertThat(error.userFacingMessage("Message failed."))
            .isEqualTo("The request timed out. Check your connection and retry.")
    }
}
