package com.example.jiotvprovider

import android.content.Context
import android.text.InputType
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.*

/**
 * JioTVSettings — provides the in-app login UI for the JioTV provider.
 *
 * CloudStream calls [settingsLoadedCallback] when the user opens the plugin
 * Settings screen. We paint a two-step OTP login flow there:
 *   Step 1 — Enter Jio mobile number  →  "Send OTP"
 *   Step 2 — Enter the received OTP   →  "Verify & Login"
 *
 * All UI is built using the standard CloudStream preference DSL so it renders
 * correctly in both the Android TV and phone layouts.
 */
class JioTVSettings(private val provider: JioTVProvider) : Plugin.SettingsLoader() {

    override fun settingsLoadedCallback(context: Context) {
        /* -------------------------------------------------------------------
         * We use CloudStream's addPreferencesFromResource-equivalent DSL.
         * Each call to addEditTextPreference / addPreferenceRow adds a row to
         * the plugin's settings screen.
         * ----------------------------------------------------------------- */

        // ── Show current login state ──────────────────────────────────────
        val loggedIn = provider.isLoggedIn()

        addPreferenceRow(
            key         = "jiotv_login_status",
            title       = if (loggedIn) "✅  Logged in to JioTV" else "❌  Not logged in",
            summary     = if (loggedIn) "Tap to log out" else "Enter your Jio number below and request an OTP",
            onClick     = if (loggedIn) {
                {
                    provider.logout()
                    Toast.makeText(context, "Logged out of JioTV", Toast.LENGTH_SHORT).show()
                    // Reload settings to refresh the status row
                    settingsLoadedCallback(context)
                }
            } else null
        )

        if (!loggedIn) {
            // ── Step 1: Mobile number input ───────────────────────────────
            addEditTextPreference(
                key         = "jiotv_mobile_input",
                title       = "1️⃣  Jio Mobile Number",
                summary     = "10-digit number registered with Jio (without country code)",
                default     = "",
                inputType   = InputType.TYPE_CLASS_NUMBER,
                maxLength   = 10
            )

            // ── Step 1: Send OTP button ───────────────────────────────────
            addPreferenceRow(
                key     = "jiotv_send_otp",
                title   = "📲  Send OTP",
                summary = "Tap to request a 6-digit OTP via SMS",
                onClick = {
                    val mobile = getPreferenceString(context, "jiotv_mobile_input", "")
                    if (mobile.isNullOrBlank() || mobile.length != 10) {
                        Toast.makeText(context, "Please enter a valid 10-digit Jio mobile number.", Toast.LENGTH_LONG).show()
                        return@addPreferenceRow
                    }
                    Toast.makeText(context, "Sending OTP to $mobile …", Toast.LENGTH_SHORT).show()
                    CoroutineScope(Dispatchers.IO).launch {
                        val ok = provider.sendOtp(mobile)
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                Toast.makeText(context, "OTP sent! Check your SMS.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to send OTP. Check your number and try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            // ── Step 2: OTP input ─────────────────────────────────────────
            addEditTextPreference(
                key       = "jiotv_otp_input",
                title     = "2️⃣  Enter OTP",
                summary   = "6-digit code sent to your Jio number",
                default   = "",
                inputType = InputType.TYPE_CLASS_NUMBER,
                maxLength = 6
            )

            // ── Step 2: Verify OTP button ─────────────────────────────────
            addPreferenceRow(
                key     = "jiotv_verify_otp",
                title   = "✔️  Verify & Login",
                summary = "Submit the OTP to complete login",
                onClick = {
                    val mobile = getPreferenceString(context, "jiotv_mobile_input", "")
                    val otp    = getPreferenceString(context, "jiotv_otp_input",    "")
                    if (mobile.isNullOrBlank() || mobile.length != 10) {
                        Toast.makeText(context, "Enter your 10-digit mobile number first.", Toast.LENGTH_LONG).show()
                        return@addPreferenceRow
                    }
                    if (otp.isNullOrBlank() || otp.length != 6) {
                        Toast.makeText(context, "Enter the 6-digit OTP from your SMS.", Toast.LENGTH_LONG).show()
                        return@addPreferenceRow
                    }
                    Toast.makeText(context, "Verifying OTP …", Toast.LENGTH_SHORT).show()
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = provider.verifyOtp(mobile, otp)
                        withContext(Dispatchers.Main) {
                            if (result == "success") {
                                Toast.makeText(context, "✅ Logged in to JioTV successfully!", Toast.LENGTH_LONG).show()
                                settingsLoadedCallback(context)  // refresh settings screen
                            } else {
                                Toast.makeText(context, "Login failed: $result", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }

        // ── Info row ──────────────────────────────────────────────────────
        addPreferenceRow(
            key     = "jiotv_info",
            title   = "ℹ️  About JioTV Plugin",
            summary = "Streams live JioTV channels. Requires an active Jio SIM / account. " +
                      "Credentials are stored securely on this device only."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: read from CloudStream's shared preferences for this plugin
    // ─────────────────────────────────────────────────────────────────────────
    private fun getPreferenceString(context: Context, key: String, default: String): String? =
        context.getSharedPreferences("jiotv_plugin_prefs", Context.MODE_PRIVATE)
            .getString(key, default)
}
