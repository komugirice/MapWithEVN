package com.example.mapapp

class AllImage {
    var allImage : List<ImageData> = listOf()
}

class ImageData {
    var lat = 0.0
    var lon = 0.0
    var filePath = ""
    var id = System.currentTimeMillis()
}