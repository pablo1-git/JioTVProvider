package com.example.jiotvprovider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

/**
 * JioTVProvider — A CloudStream 3 provider for JioTV live channels.
 *
 * Authentication: OTP-based (Jio mobile number + 6-digit SMS OTP).
 * Credentials are stored in SharedPreferences via CloudStream's settings API.
 *
 * Category / Language maps mirror the jiotv_go television package.
 */
class JioTVProvider : MainAPI() {

    // ─────────────────────────────────────────────────────────────────────────
    // Plugin identity
    // ─────────────────────────────────────────────────────────────────────────
    override var mainUrl              = "https://jiotvapi.media.jio.com"
    override var name                 = "JioTV"
    override val hasMainPage          = true
    override val hasSearch            = true
    override val supportedTypes       = setOf(TvType.Live)
    override var lang                 = "hi"   // default; channels span many languages
    override val hasQuickSearch       = false

    // CDN / stream base
    private val JIOTV_CDN            = "https://jiotv.media.cdn.jio.com"
    private val CHANNELS_API         = "$mainUrl/apis/v3.0/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=tvYR7NSNn7rymo3F&version=315"
    private val LIVE_URL_API         = "$mainUrl/apis/v1.3/geturl/get/"
    private val OTP_SEND_URL         = "$mainUrl/userservice/apis/v1/loginotp/send"
    private val OTP_VERIFY_URL       = "$mainUrl/userservice/apis/v1/loginotp/verify"

    // Logo CDN image base
    private val LOGO_BASE_URL        = "https://jiotv.catchup.cdn.jio.com/apis/v1.3/fetchChannelLogos/get/?channel_id="

    // ─────────────────────────────────────────────────────────────────────────
    // SharedPreference keys (persisted by CloudStream)
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        const val PREF_ACCESS_TOKEN  = "jiotv_access_token"
        const val PREF_SSO_TOKEN     = "jiotv_sso_token"
        const val PREF_CRM           = "jiotv_crm"
        const val PREF_UNIQUE_ID     = "jiotv_unique_id"
        const val PREF_SUBSCRIBER_ID = "jiotv_subscriber_id"

        /** Category IDs → human-readable labels (mirrors tv package CategoryMap) */
        val CATEGORY_MAP = mapOf(
            "0"  to "All",
            "5"  to "Entertainment",
            "6"  to "Movies",
            "7"  to "Kids",
            "8"  to "Sports",
            "9"  to "Lifestyle",
            "10" to "Infotainment",
            "12" to "News",
            "13" to "Music",
            "15" to "Devotional",
            "16" to "Business",
            "17" to "Educational",
            "18" to "Shopping",
            "19" to "JioDarshan"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plugin settings (shown in CloudStream Settings → Plugin Settings)
    // ─────────────────────────────────────────────────────────────────────────
    override val settingsGenerator by lazy {
        JioTVSettings(this)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers: credentials & headers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if the user has completed OTP login. */
    fun isLoggedIn(): Boolean =
        getPreferenceString(PREF_ACCESS_TOKEN, null) != null

    /** Builds authenticated request headers for JioTV API calls. */
    private fun buildHeaders(): Map<String, String> {
        val accessToken   = getPreferenceString(PREF_ACCESS_TOKEN, "")  ?: ""
        val ssoToken      = getPreferenceString(PREF_SSO_TOKEN, "")     ?: ""
        val crm           = getPreferenceString(PREF_CRM, "")           ?: ""
        val uniqueId      = getPreferenceString(PREF_UNIQUE_ID, "")     ?: ""
        val subscriberId  = getPreferenceString(PREF_SUBSCRIBER_ID, "") ?: ""

        return mapOf(
            "accessToken"       to accessToken,
            "os"                to "android",
            "devicetype"        to "phone",
            "usertype"          to "tvYR7NSNn7rymo3F",
            "version"           to "315",
            "versionCode"       to "315",
            "langId"            to "6",
            "crmId"             to crm,
            "subscriberId"      to subscriberId,
            "uniqueId"          to uniqueId,
            "ssoToken"          to ssoToken,
            "channelId"         to "",
            "x-api-key"         to "l7XX330d275gg2tCO8DDgJXEJisXXXX",
            "User-Agent"        to "okhttp/4.2.2",
            "Content-Type"      to "application/json; charset=utf-8"
        )
    }

    /** Minimal headers for unauthenticated OTP requests. */
    private val otpHeaders = mapOf(
        "appname"        to "RJIL_JioTV",
        "os"             to "android",
        "devicetype"     to "phone",
        "Content-Type"   to "application/json",
        "User-Agent"     to "okhttp/4.2.2"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Home page — one row per category
    // ─────────────────────────────────────────────────────────────────────────
    override val mainPage = mainPageOf(
        "5"  to "Entertainment",
        "12" to "News",
        "8"  to "Sports",
        "6"  to "Movies",
        "13" to "Music",
        "7"  to "Kids",
        "9"  to "Lifestyle",
        "10" to "Infotainment",
        "15" to "Devotional",
        "16" to "Business",
        "17" to "Educational"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (!isLoggedIn()) throw ErrorLoadingException("Please log in to JioTV first. Go to ⚙ Settings → JioTV → Login.")

        val categoryId = request.data
        val channels   = fetchChannels()
        val results    = channels
            .filter { ch -> ch.categoryId.toString() == categoryId }
            .map    { it.toSearchResponse() }

        return newHomePageResponse(request.name, results, hasNextPage = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        if (!isLoggedIn()) throw ErrorLoadingException("Please log in to JioTV first.")
        return fetchChannels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map    { it.toSearchResponse() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load (channel detail page → one "episode" = the live stream)
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        // url format: "jiotv://channel/<channelId>/<channelName>/<logoUrl>/<isHD>/<categoryId>"
        val parts       = url.removePrefix("jiotv://channel/").split("/")
        val channelId   = parts.getOrElse(0) { "" }
        val channelName = parts.getOrElse(1) { "Unknown Channel" }
        val logoUrl     = parts.getOrElse(2) { "" }.replace("|", "/")
        val isHD        = parts.getOrElse(3) { "false" } == "true"
        val categoryId  = parts.getOrElse(4) { "0" }
        val categoryName = CATEGORY_MAP[categoryId] ?: "Live"

        // Build a single "episode" that carries the channelId as data
        val episode = newEpisode(channelId) {
            this.name = if (isHD) "🔴 Live (HD)" else "🔴 Live"
        }

        return newTvSeriesLoadResponse(
            channelName,
            url,
            TvType.Live,
            listOf(episode)
        ) {
            this.posterUrl  = logoUrl
            this.tags       = listOf(categoryName, if (isHD) "HD" else "SD")
            this.plot       = "Live channel: $channelName • Category: $categoryName"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load links — get the actual HLS stream URL for a channel
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channelId = data   // episode data is just the channelId string
        val liveOutput = fetchLiveUrl(channelId)
            ?: throw ErrorLoadingException("Failed to get stream URL for channel $channelId. Are you logged in?")

        // Add each quality level as a separate ExtractorLink
        val bitrates = liveOutput.bitrates
        val qualities = mapOf(
            "Auto"   to bitrates.auto,
            "High"   to bitrates.high,
            "Medium" to bitrates.medium,
            "Low"    to bitrates.low
        )

        var found = false
        qualities.forEach { (label, streamUrl) ->
            if (!streamUrl.isNullOrBlank()) {
                callback(
                    ExtractorLink(
                        source       = this.name,
                        name         = "JioTV $label",
                        url          = streamUrl,
                        referer      = mainUrl,
                        quality      = when (label) {
                            "High"   -> Qualities.P1080.value
                            "Medium" -> Qualities.P720.value
                            "Low"    -> Qualities.P480.value
                            else     -> Qualities.Unknown.value
                        },
                        isM3u8       = true,
                        headers      = buildHeaders()
                    )
                )
                found = true
            }
        }
        return found
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetches all channels from the JioTV API. */
    private suspend fun fetchChannels(): List<JioChannel> {
        val response = app.get(CHANNELS_API, headers = buildHeaders()).text
        val parsed   = parseJson<ChannelListResponse>(response)
        return parsed.result ?: emptyList()
    }

    /** Fetches the live HLS URL for a single channelId. */
    private suspend fun fetchLiveUrl(channelId: String): LiveURLOutput? {
        val url      = "$LIVE_URL_API?channel_id=$channelId&stream_type=Smooth"
        val response = app.get(url, headers = buildHeaders())
        if (!response.isSuccessful) return null
        return parseJson<LiveURLOutput>(response.text)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OTP Login helpers (called from JioTVSettings)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 1 — Request an OTP to be sent to [mobileNumber].
     * Returns true on HTTP 204 (success), false otherwise.
     */
    suspend fun sendOtp(mobileNumber: String): Boolean {
        val encodedNumber = android.util.Base64.encodeToString(
            mobileNumber.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val body     = """{"number":"$encodedNumber"}"""
        val response = app.post(OTP_SEND_URL, headers = otpHeaders, requestBody = body.toRequestBody())
        // 204 = No Content → OTP sent successfully
        return response.code == 204 || response.isSuccessful
    }

    /**
     * Step 2 — Verify the OTP and persist tokens if successful.
     * Returns a descriptive result string ("success" or an error message).
     */
    suspend fun verifyOtp(mobileNumber: String, otp: String): String {
        val encodedNumber = android.util.Base64.encodeToString(
            mobileNumber.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        // Generate a random Android device ID (16 hex chars = 8 bytes)
        val androidId = (1..8)
            .map { (0..255).random().toString(16).padStart(2, '0') }
            .joinToString("")

        val body = """
            {
              "number": "$encodedNumber",
              "otp": "$otp",
              "deviceInfo": {
                "consumptionDeviceName": "CloudStream",
                "info": {
                  "type": "android",
                  "platform": {
                    "name": "Android",
                    "version": "13"
                  },
                  "androidId": "$androidId"
                }
              }
            }
        """.trimIndent()

        val response = app.post(OTP_VERIFY_URL, headers = otpHeaders, requestBody = body.toRequestBody())
        if (!response.isSuccessful) {
            return "OTP verification failed (HTTP ${response.code}). Please try again."
        }

        return try {
            val result = parseJson<OtpVerifyResponse>(response.text)
            // Persist all credentials
            putPreferenceString(PREF_ACCESS_TOKEN,  result.authToken       ?: return "No authToken in response")
            putPreferenceString(PREF_SSO_TOKEN,     result.ssoToken        ?: "")
            putPreferenceString(PREF_CRM,           result.sessionAttributes?.user?.subscriberId ?: "")
            putPreferenceString(PREF_SUBSCRIBER_ID, result.sessionAttributes?.user?.subscriberId ?: "")
            putPreferenceString(PREF_UNIQUE_ID,     result.sessionAttributes?.user?.unique       ?: "")
            "success"
        } catch (e: Exception) {
            "Failed to parse login response: ${e.message}"
        }
    }

    /** Clears all persisted credentials (logout). */
    fun logout() {
        putPreferenceString(PREF_ACCESS_TOKEN,  null)
        putPreferenceString(PREF_SSO_TOKEN,     null)
        putPreferenceString(PREF_CRM,           null)
        putPreferenceString(PREF_SUBSCRIBER_ID, null)
        putPreferenceString(PREF_UNIQUE_ID,     null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal data models
    // ─────────────────────────────────────────────────────────────────────────

    private data class ChannelListResponse(
        val result: List<JioChannel>?
    )

    data class JioChannel(
        val channel_id   : Int     = 0,
        val channel_name : String  = "",
        val channel_url  : String  = "",
        val logoUrl      : String  = "",
        val channelCategoryId  : Int    = 0,
        val channelLanguageId  : Int    = 0,
        val isHD               : Boolean = false,
        val isCatchupAvailable : Boolean = false
    ) {
        val id         get() = channel_id.toString()
        val name       get() = channel_name
        val categoryId get() = channelCategoryId

        /** Builds the internal "url" that load() will receive. */
        val internalUrl: String get() {
            // Encode logo URL by replacing '/' with '|' (safe for URL path segment)
            val safeLogo = logoUrl.replace("/", "|")
            return "jiotv://channel/$id/${name.replace("/","-")}/$safeLogo/$isHD/$categoryId"
        }

        fun toSearchResponse(): LiveSearchResponse =
            newLiveSearchResponse(
                name      = name,
                url       = internalUrl,
                type      = TvType.Live
            ) {
                this.posterUrl = logoUrl
                this.lang      = channelLanguageId.toString()
            }
    }

    private data class LiveURLOutput(
        val bitrates   : Bitrates  = Bitrates(),
        val code       : Int       = 0,
        val message    : String    = "",
        val result     : String    = ""
    )

    private data class Bitrates(
        val auto   : String? = null,
        val high   : String? = null,
        val medium : String? = null,
        val low    : String? = null
    )

    private data class OtpVerifyResponse(
        val authToken          : String?           = null,
        val refreshToken       : String?           = null,
        val ssoToken           : String?           = null,
        val sessionAttributes  : SessionAttributes? = null
    )

    private data class SessionAttributes(
        val user: UserAttributes? = null
    )

    private data class UserAttributes(
        val subscriberId : String? = null,
        val unique       : String? = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Preference helpers (thin wrappers so settings file can call them too)
    // ─────────────────────────────────────────────────────────────────────────

    fun getPreferenceString(key: String, default: String?): String? =
        getSharedPreferences()?.getString(key, default)

    fun putPreferenceString(key: String, value: String?) {
        getSharedPreferences()?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
            apply()
        }
    }
}

/** Helper to create a plain-text JSON request body for app.post(). */
private fun String.toRequestBody(): okhttp3.RequestBody =
    okhttp3.RequestBody.create(
        okhttp3.MediaType.parse("application/json; charset=utf-8"),
        this
    )
