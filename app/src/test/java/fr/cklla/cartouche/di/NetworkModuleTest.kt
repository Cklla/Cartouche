package fr.cklla.cartouche.di

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie ce qui sort réellement dans les journaux, sans réseau : un faux « serveur » renvoie une
 * réponse Twitch contenant un jeton, et on lit ce que le journal HTTP en a écrit.
 */
class NetworkModuleTest {

    private val token = "JETON-SECRET-TWITCH"
    private val clientSecret = "SECRET-CLIENT-TWITCH"
    private val clientId = "ID-CLIENT-TWITCH"

    private val lines = mutableListOf<String>()
    private val logger = HttpLoggingInterceptor.Logger { lines += it }

    /** Terminaison de la chaîne : répond comme `oauth2/token`, avec le jeton dans le corps. */
    private val fakeTwitchServer = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"access_token":"$token","expires_in":5000000,"token_type":"bearer"}""".toResponseBody())
            .build()
    }

    private fun callTwitch(client: OkHttpClient) {
        val request = Request.Builder()
            .url("https://id.twitch.tv/oauth2/token?client_id=$clientId&client_secret=$clientSecret&grant_type=client_credentials")
            .header("Authorization", "Bearer $token")
            .header("Client-ID", clientId)
            .post("".toRequestBodyForTest())
            .build()
        client.newBuilder().addInterceptor(fakeTwitchServer).build().newCall(request).execute().use { it.body.string() }
    }

    private fun String.toRequestBodyForTest() = okhttp3.RequestBody.create(null, this)

    private fun journal() = lines.joinToString("\n")

    @Test
    fun `le client Twitch n'ecrit jamais le jeton de la reponse dans les journaux, meme en debug`() {
        // Client de base comme en production : journal au niveau BODY.
        val base = OkHttpClient.Builder()
            .addInterceptor(redactedLoggingInterceptor(HttpLoggingInterceptor.Level.BODY, logger))
            .build()

        callTwitch(twitchHttpClient(base, debug = true, logger = logger))

        assertTrue("le journal doit exister", lines.isNotEmpty())
        assertFalse("jeton dans le corps", journal().contains(token))
        assertFalse("secret client dans l'URL", journal().contains(clientSecret))
        assertFalse("identifiant client", journal().contains(clientId))
        assertFalse("en-tête Authorization", journal().contains("Bearer"))
        assertFalse("le corps n'est pas journalisé", journal().contains("access_token"))
    }

    @Test
    fun `le client Twitch ne garde aucun journal de corps du client de base`() {
        val base = OkHttpClient.Builder()
            .addInterceptor(redactedLoggingInterceptor(HttpLoggingInterceptor.Level.BODY, logger))
            .build()

        val twitch = twitchHttpClient(base, debug = true, logger = logger)

        // Un seul journal, et il est au niveau HEADERS.
        val loggers = twitch.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        assertTrue(loggers.size == 1)
        assertTrue(loggers.single().level == HttpLoggingInterceptor.Level.HEADERS)
    }

    @Test
    fun `en release le client Twitch n'ecrit rien`() {
        val base = OkHttpClient.Builder()
            .addInterceptor(redactedLoggingInterceptor(HttpLoggingInterceptor.Level.NONE, logger))
            .build()

        callTwitch(twitchHttpClient(base, debug = false, logger = logger))

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `le journal du client de base masque les en-tetes et les parametres d'URL secrets`() {
        // Ce niveau BODY n'est sûr que pour des réponses sans secret (IGDB) : on vérifie ici ce
        // que le masquage couvre bien (en-têtes et URL), pas le corps.
        val igdbLike = OkHttpClient.Builder()
            .addInterceptor(redactedLoggingInterceptor(HttpLoggingInterceptor.Level.BODY, logger))
            .build()

        callTwitch(igdbLike)

        assertFalse(journal().contains(clientSecret))
        assertFalse(journal().contains(clientId))
        assertFalse(journal().contains("Bearer"))
    }
}
