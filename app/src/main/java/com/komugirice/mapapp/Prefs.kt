package com.komugirice.mapapp

import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.komugirice.mapapp.MyApplication.Companion.applicationContext
import com.google.gson.Gson
import com.komugirice.mapapp.data.AllImage
import io.reactivex.Observable
import timber.log.Timber

/**
 * @author Jane
 */
class Prefs {

    var allImage = AllImageEntry("allImages")
    val mode by lazy{ IntEntry("mode")}
    val notebookName by lazy { StringEntry("notebookName")}

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

    class StringEntry(private val key: String, private val defaultValue: String = "") : Entry<String> {
        override fun put(value: String) {
            Timber.d("put $key -> $value")
            getSharedPreference().edit().putString(key, value).apply()
        }

        override fun get(): Observable<String> {
            return createObservable(getSharedPreference(), key) {
                it.getString(key, defaultValue) ?: defaultValue
            }.onErrorReturn {
                defaultValue
            }
        }

        override fun remove() = getSharedPreference().edit().remove(key).apply()

    }

    class IntEntry(private val key: String, private val defaultValue: Int = 0) : Entry<Int> {
        override fun put(value: Int) {
            Timber.d("put $key -> $value")
            getSharedPreference().edit().putInt(key, value).apply()
        }

        override fun get(): Observable<Int> {
            return createObservable(getSharedPreference(), key) {
                it.getInt(key, defaultValue)
            }.onErrorReturn {
                put(defaultValue)
                defaultValue
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