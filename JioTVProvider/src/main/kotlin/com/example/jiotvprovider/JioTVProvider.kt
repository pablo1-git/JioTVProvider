package com.example.jiotvprovider

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

/**
 * JioTVProvider — A CloudStream 3 provider for JioTV live channels.
 *
 * Authentication: OTP-based (Jio mobile number + 6-digit SMS OTP).
 * Credentials are stored in SharedPreferences via getSharedPreferences().
 */
class JioTVProvider : MainAPI() {

    // ─────────────────────────────────────────────────────────────────────────
    // Plugin identity
    // ─────────────────────────────────────────────────────────────────────────
    override var mainUrl         = "https://jiotvapi.media.jio.com"
    override var name            = "JioTV"
    override val hasMainPage     = true
    override val hasSearch       = true
    override val supportedTypes  = setOf(TvType.Live)
    override var lang            = "hi"
    override val hasQuickSearch  = false

    private val CHANNELS_API   = "$mainUrl/apis/v3.0/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=tvYR7NSNn7rymo3F&version=315"
    private val LIVE_URL_API   = "$mainUrl/apis/v1.3/geturl/get/"
    private val OTP_SEND_URL   = "$mainUrl/userservice/apis/v1/loginotp/send"
    private val OTP_VERIFY_URL = "$mainUrl/userservice/apis/v1/loginotp/verify"

    // ─────────────────────────────────────────────────────────────────────────
    // SharedPreference keys
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        const val PREF_ACCESS_TOKEN  = "jiotv_access_token"
        const val PREF_SSO_TOKEN     = "jiotv_sso_token"
        const val PREF_CRM           = "jiotv_crm"
        const val PREF_UNIQUE_ID     = "jiotv_unique_id"
        const val PREF_SUBSCRIBER_ID = "jiotv_subscriber_id"

        val CATEGORY_MAP = mapOf(
            "0" to "All", "5" to "Entertainment", "6" to "Movies",
            "7" to "Kids", "8" to "Sports", "9" to "Lifestyle",
            "10" to "Infotainment", "12" to "News", "13" to "Music",
            "15" to "Devotional", "16" to "Business", "17" to "Educational"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────────────────────────────────
    override val settingsGenerator by lazy { JioTVSettings(this) }

    // ─────────────────────────────────────────────────────────────────────────
    // Credential helpers
    // ─────────────────────────────────────────────────────────────────────────
    fun isLoggedIn() = getPreferenceString(PREF_ACCESS_TOKEN, null) != null

    private fun buildHeaders(): Map<String, String> {
        return mapOf(
            "accessToken"  to (getPreferenceString(PREF_ACCESS_TOKEN, "") ?: ""),
            "os"           to "android",
            "devicetype"   to "phone",
            "usertype"     to "tvYR7NSNn7rymo3F",
            "version"      to "315",
            "versionCode"  to "315",
            "langId"       to "6",
            "crmId"        to (getPreferenceString(PREF_CRM, "") ?: ""),
            "subscriberId" to (getPreferenceString(PREF_SUBSCRIBER_ID, "") ?: ""),
            "uniqueId"     to (getPreferenceString(PREF_UNIQUE_ID, "") ?: ""),
            "ssoToken"     to (getPreferenceString(PREF_SSO_TOKEN, "") ?: ""),
            "channelId"    to "",
            "x-api-key"   to "l7XX330d275gg2tCO8DDgJXEJisXXXX",
            "User-Agent"   to "okhttp/4.2.2",
            "Content-Type" to "application/json; charset=utf-8"
        )
    }

    private val otpHeaders = mapOf(
        "appname"      to "RJIL_JioTV",
        "os"           to "android",
        "devicetype"   to "phone",
        "Content-Type" to "application/json",
        "User-Agent"   to "okhttp/4.2.2"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Home page
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
        if (!isLoggedIn()) throw ErrorLoadingException("Please log in to JioTV first. Go to ⚙ Settings → JioTV → Plugin Settings.")
        val results = fetchChannels()
            .filter { it.channelCategoryId.toString() == request.data }
            .map { it.toSearchResponse() }
        return newHomePageResponse(request.name, results, hasNextPage = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        if (!isLoggedIn()) throw ErrorLoadingException("Please log in to JioTV first.")
        return fetchChannels()
            .filter { it.channel_name.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        val parts       = url.removePrefix("jiotv://channel/").split("/")
        val channelId   = parts.getOrElse(0) { "" }
        val channelName = parts.getOrElse(1) { "JioTV Channel" }
        val logoUrl     = parts.getOrElse(2) { "" }.replace("|", "/")
        val isHD        = parts.getOrElse(3) { "false" } == "true"
        val categoryId  = parts.getOrElse(4) { "0" }
        val categoryName = CATEGORY_MAP[categoryId] ?: "Live"

        val episode = newEpisode(channelId) {
            name = if (isHD) "🔴 Live (HD)" else "🔴 Live"
        }

        return newTvSeriesLoadResponse(channelName, url, TvType.Live, listOf(episode)) {
            posterUrl = logoUrl
            tags      = listOf(categoryName, if (isHD) "HD" else "SD")
            plot      = "Live channel: $channelName | Category: $categoryName"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load links
    // ─────────────────────────────────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val liveOutput = fetchLiveUrl(data)
            ?: throw ErrorLoadingException("Failed to get stream URL. Are you logged in?")

        val bitrates = liveOutput.bitrates
        listOf(
            "Auto"   to bitrates.auto,
            "High"   to bitrates.high,
            "Medium" to bitrates.medium,
            "Low"    to bitrates.low
        ).forEach { (label, streamUrl) ->
            if (!streamUrl.isNullOrBlank()) {
                callback(ExtractorLink(
                    source  = name,
                    name    = "JioTV $label",
                    url     = streamUrl,
                    referer = mainUrl,
                    quality = when (label) {
                        "High"   -> Qualities.P1080.value
                        "Medium" -> Qualities.P720.value
                        "Low"    -> Qualities.P480.value
                        else     -> Qualities.Unknown.value
                    },
                    isM3u8  = true,
                    headers = buildHeaders()
                ))
            }
        }
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network helpers
    // ─────────────────────────────────────────────────────────────────────────
    private suspend fun fetchChannels(): List<JioChannel> {
        val text = app.get(CHANNELS_API, headers = buildHeaders()).text
        return parseJson<ChannelListResponse>(text).result ?: emptyList()
    }

    private suspend fun fetchLiveUrl(channelId: String): LiveURLOutput? {
        val response = app.get("$LIVE_URL_API?channel_id=$channelId&stream_type=Smooth", headers = buildHeaders())
        return if (response.isSuccessful) parseJson<LiveURLOutput>(response.text) else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OTP helpers
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun sendOtp(mobileNumber: String): Boolean {
        val encoded = Base64.encodeToString(mobileNumber.toByteArray(), Base64.NO_WRAP)
        val body = """{"number":"$encoded"}"""
        val response = app.post(OTP_SEND_URL, headers = otpHeaders, json = mapOf("number" to encoded))
        return response.code == 204 || response.isSuccessful
    }

    suspend fun verifyOtp(mobileNumber: String, otp: String): String {
        val encoded   = Base64.encodeToString(mobileNumber.toByteArray(), Base64.NO_WRAP)
        val androidId = (1..8).joinToString("") { (0..255).random().toString(16).padStart(2, '0') }

        val response = app.post(
            OTP_VERIFY_URL,
            headers = otpHeaders,
            json = mapOf(
                "number" to encoded,
                "otp"    to otp,
                "deviceInfo" to mapOf(
                    "consumptionDeviceName" to "CloudStream",
                    "info" to mapOf(
                        "type"     to "android",
                        "platform" to mapOf("name" to "Android", "version" to "13"),
                        "androidId" to androidId
                    )
                )
            )
        )

        if (!response.isSuccessful) return "OTP failed (HTTP ${response.code}). Try again."

        return try {
            val r = parseJson<OtpVerifyResponse>(response.text)
            putPreferenceString(PREF_ACCESS_TOKEN,  r.authToken ?: return "No authToken in response")
            putPreferenceString(PREF_SSO_TOKEN,     r.ssoToken ?: "")
            putPreferenceString(PREF_CRM,           r.sessionAttributes?.user?.subscriberId ?: "")
            putPreferenceString(PREF_SUBSCRIBER_ID, r.sessionAttributes?.user?.subscriberId ?: "")
            putPreferenceString(PREF_UNIQUE_ID,     r.sessionAttributes?.user?.unique ?: "")
            "success"
        } catch (e: Exception) {
            "Parse error: ${e.message}"
        }
    }

    fun logout() {
        listOf(PREF_ACCESS_TOKEN, PREF_SSO_TOKEN, PREF_CRM, PREF_SUBSCRIBER_ID, PREF_UNIQUE_ID)
            .forEach { putPreferenceString(it, null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data models
    // ─────────────────────────────────────────────────────────────────────────
    private data class ChannelListResponse(val result: List<JioChannel>?)

    data class JioChannel(
        val channel_id         : Int     = 0,
        val channel_name       : String  = "",
        val logoUrl            : String  = "",
        val channelCategoryId  : Int     = 0,
        val channelLanguageId  : Int     = 0,
        val isHD               : Boolean = false
    ) {
        private val internalUrl: String get() {
            val safeLogo = logoUrl.replace("/", "|")
            return "jiotv://channel/$channel_id/${channel_name.replace("/", "-")}/$safeLogo/$isHD/$channelCategoryId"
        }

        fun toSearchResponse(): LiveSearchResponse =
            newLiveSearchResponse(channel_name, internalUrl, TvType.Live) {
                posterUrl = logoUrl
            }
    }

    private data class LiveURLOutput(
        val bitrates : Bitrates = Bitrates(),
        val code     : Int      = 0,
        val result   : String   = ""
    )

    private data class Bitrates(
        val auto   : String? = null,
        val high   : String? = null,
        val medium : String? = null,
        val low    : String? = null
    )

    private data class OtpVerifyResponse(
        val authToken         : String?            = null,
        val ssoToken          : String?            = null,
        val sessionAttributes : SessionAttributes? = null
    )

    private data class SessionAttributes(val user: UserAttributes? = null)
    private data class UserAttributes(val subscriberId: String? = null, val unique: String? = null)

    // ─────────────────────────────────────────────────────────────────────────
    // Preference helpers
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
