package com.example.mapapp

import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.example.mapapp.MyApplication.Companion.applicationContext
import com.google.gson.Gson
import io.reactivex.Observable

class Prefs {

    var allImage = AllImageEntry("allImages")

    interface Entry<T> {
        fun put(value: T)
        fun get(): Observable<T>
        fun remove()
    }

    class AllImageEntry(private val key: String) : Entry<AllImage> {
        val gson: Gson = Gson()

        override fun put(value: AllImage) {
            getSharedPreference().edit().putString(key, gson.toJson(value)).apply()
        }

        override fun get(): Observable<AllImage> {
            return createObservable(getSharedPreference(), key) {
                gson.fromJson(it.getString(key, ""), AllImage::class.java)
            }.onErrorReturn {
                AllImage()
            }
        }

        override fun remove() = getSharedPreference().edit().remove(key).apply()
    }

    class EntryNotFoundException : Exception {
        constructor(detailMessage: String?) : super(detailMessage)
    }

    companion object {
        fun getSharedPreference(): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(applicationContext)
        }

        fun <T> createObservable(preferences: SharedPreferences, key: String, valueF: (SharedPreferences) -> T): Observable<T> {
            return Observable.create { subscriber ->
                try {
                    if (preferences.contains(key)) {
                        subscriber.onNext(valueF(preferences))
                        subscriber.onComplete()
                    } else {
                        subscriber.onError(EntryNotFoundException("Not found $key in SharedPreferences"))
                    }
                } catch (e: Exception) {
                    subscriber.onError(e)
                }
            }
        }

    }
}