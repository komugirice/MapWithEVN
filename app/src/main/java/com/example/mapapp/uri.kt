package com.example.mapapp

import android.net.Uri
import com.example.mapapp.MyApplication.Companion.applicationContext
import java.io.File
import java.io.FileOutputStream

fun Uri.makeTempFile(): File? {
    val fileName = "${System.currentTimeMillis()}"
    val file = File.createTempFile(fileName, ".temp", applicationContext.cacheDir)
    val inputStream = applicationContext.contentResolver.openInputStream(this)
    if (inputStream != null) {
        val fileOutputStream = FileOutputStream(file)
        val buffer = ByteArray(1024)
        while (true) {
            val length = inputStream.read(buffer)
            if (length <= 0)
                break
            fileOutputStream.write(buffer, 0, length)
        }
        return file
    }
    return null
}