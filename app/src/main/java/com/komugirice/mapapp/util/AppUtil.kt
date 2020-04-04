package com.komugirice.mapapp.util

import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import timber.log.Timber
import java.io.File

object AppUtil {
    fun deleteCacheDir() {
        val cacheDir = File(StringBuilder().append(applicationContext.cacheDir).toString())
        Timber.d("cacheDir.exists:\${cacheDir.exists()}")
        if (!cacheDir.exists())
            return
        cacheDir.listFiles().forEach {
            Timber.d("fileName:${it.name}")
            if (it.exists()) {
                val isDeleted = it.delete()
                Timber.d("isDeleted:$isDeleted")
            }
        }
    }
}