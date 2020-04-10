package com.komugirice.mapapp

import java.io.Serializable

/**
 * @author Jane
 */
class AllImage {
    var allImage : List<ImageData> = listOf()
}

open class ImageData {
    var lat = 0.0
    var lon = 0.0
    var filePath = ""
    var address = ""
    var id = System.currentTimeMillis()
}

class EvImageData: ImageData() {
    var guid = ""
    var noteGuid = ""

    fun applyImageData(i: ImageData) {
        this.lat = i.lat
        this.lon = i.lon
        this.filePath = i.filePath
        this.address = i.address
        this.id = i.id
    }
}