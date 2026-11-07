package com.telestream.tv

import android.app.Application

class TeleStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
