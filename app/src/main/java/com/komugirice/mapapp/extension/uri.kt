package com.komugirice.mapapp.extension

import android.net.Uri
import android.os.Environment
import com.komugirice.mapapp.MyApplication
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import java.io.File
import java.io.FileOutputStream

/**
 * @author Jane
 */
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

fun Uri.makeTempFileToStorage(): File? {
    val fileName = "${System.currentTimeMillis()}"
    val storageDir: File? = MyApplication.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val file = File.createTempFile(fileName, ".temp", storageDir)
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