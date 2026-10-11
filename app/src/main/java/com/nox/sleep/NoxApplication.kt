package com.nox.sleep

import android.app.Application
import com.nox.sleep.data.NoxDatabase
import com.nox.sleep.data.SettingsStore
import com.nox.sleep.data.SleepRepository

class NoxApplication : Application() {

    val database: NoxDatabase by lazy { NoxDatabase.get(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repo: SleepRepository by lazy { SleepRepository(database.dao(), settings) }
}
