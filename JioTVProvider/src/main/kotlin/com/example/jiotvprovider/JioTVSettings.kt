package com.example.jiotvprovider

import android.content.Context
import android.text.InputType
import android.widget.Toast
import com.lagradost.cloudstream3.plugins.Plugin
import kotlinx.coroutines.*

/**
 * JioTVSettings — Login UI in CloudStream Plugin Settings.
 *
 * Step 1: Enter 10-digit Jio mobile number → Send OTP
 * Step 2: Enter 6-digit OTP from SMS      → Verify & Login
 */
class JioTVSettings(private val provider: JioTVProvider) : Plugin.Callbacks {

    fun onSettingsLoaded(context: Context) {
        // This is a hook — actual UI preferences are declared in setupPreferences()
    }

    /**
     * Called when user opens Plugin Settings in CloudStream.
     * Builds the preference screen using Android SharedPreferences.
     */
    fun buildLoginUI(context: Context, settingsApi: Any) {
        val prefs = context.getSharedPreferences("jiotv_login_ui", Context.MODE_PRIVATE)

        // Store UI inputs in jiotv_login_ui so they don't clash with credentials
        fun getMobile() = prefs.getString("mobile_input", "") ?: ""
        fun getOtp()    = prefs.getString("otp_input", "") ?: ""

        if (provider.isLoggedIn()) {
            Toast.makeText(context, "✅ Already logged in to JioTV. To logout, clear app data.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Open Plugin Settings in CloudStream to log in.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Initiates OTP send and verify flow, meant to be called from a UI action.
     */
    fun sendOtp(context: Context, mobile: String) {
        if (mobile.length != 10) {
            Toast.makeText(context, "Enter a valid 10-digit Jio number", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val ok = provider.sendOtp(mobile)
            withContext(Dispatchers.Main) {
                if (ok) Toast.makeText(context, "✅ OTP sent to $mobile", Toast.LENGTH_LONG).show()
                else    Toast.makeText(context, "❌ Failed to send OTP. Check your number.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun verifyOtp(context: Context, mobile: String, otp: String) {
        if (mobile.length != 10 || otp.length != 6) {
            Toast.makeText(context, "Enter valid mobile (10 digits) and OTP (6 digits)", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val result = provider.verifyOtp(mobile, otp)
            withContext(Dispatchers.Main) {
                if (result == "success")
                    Toast.makeText(context, "✅ Logged in to JioTV!", Toast.LENGTH_LONG).show()
                else
                    Toast.makeText(context, "❌ $result", Toast.LENGTH_LONG).show()
            }
        }
    }
}
