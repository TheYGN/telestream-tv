package com.telestream.tv

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** What this TV remembers: the server's address, its own name and id, and the player choices. */
object Prefs {
    const val DEFAULT_SERVER = "http://192.168.7.7:8765"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("telestream", Context.MODE_PRIVATE)
        if (sp.getString("tv_id", null) == null) {
            sp.edit().putString("tv_id", UUID.randomUUID().toString().replace("-", "")).apply()
        }
    }

    var server: String
        get() = (sp.getString("server", null) ?: DEFAULT_SERVER).trimEnd('/')
        set(value) = sp.edit().putString("server", value.trim().trimEnd('/')).apply()

    val tvId: String get() = sp.getString("tv_id", "")!!

    var tvName: String
        get() = sp.getString("tv_name", "") ?: ""
        set(value) = sp.edit().putString("tv_name", value.trim()).apply()

    /** Seconds before an episode's end to offer the next one. */
    var nextLead: Int
        get() = sp.getInt("next_lead", 90)
        set(value) = sp.edit().putInt("next_lead", value.coerceIn(20, 300)).apply()

    /** 0 = show a skip button, 1 = skip automatically, 2 = never. */
    var skipMode: Int
        get() = sp.getInt("skip_mode", 0)
        set(value) = sp.edit().putInt("skip_mode", value).apply()

    /** Opening animation: 0 = with sound, 1 = silent, 2 = off. */
    var introMode: Int
        get() = sp.getInt("intro_mode", 0)
        set(value) = sp.edit().putInt("intro_mode", value).apply()

    val isSetUp: Boolean get() = tvName.isNotBlank()
}
