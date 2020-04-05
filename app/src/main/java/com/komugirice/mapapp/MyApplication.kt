package com.komugirice.mapapp

import android.app.Application
import android.content.Context
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.asyncclient.EvernoteNoteStoreClient
import com.evernote.edam.type.Notebook
import com.evernote.edam.type.User
import com.komugirice.mapapp.enums.Mode
import timber.log.Timber
import java.util.*

/**
 * @author komugirice
 */
class MyApplication: Application() {

    /*
     * ********************************************************************
     * You MUST change the following values to run this sample application.
     *
     * It's recommended to pass in these values via gradle property files.
     * ********************************************************************
     */
    /*
     * Your Evernote API key. See http://dev.evernote.com/documentation/cloud/
     * Please obfuscate your code to help keep these values secret.
     */
    private val CONSUMER_KEY = BuildConfig.CONSUMER_KEY
    private val CONSUMER_SECRET = BuildConfig.CONSUMER_SECRET
    /*
     * Initial development is done on Evernote's testing service, the sandbox.
     *
     * Change to PRODUCTION to use the Evernote production service
     * once your code is complete.
     */
    private val EVERNOTE_SERVICE = EvernoteSession.EvernoteService.SANDBOX

    /*
     * Set this to true if you want to allow linked notebooks for accounts that
     * can only access a single notebook.
     */
    private val SUPPORT_APP_LINKED_NOTEBOOKS = true


    override fun onCreate() {
        super.onCreate()
        MyApplication.applicationContext = applicationContext
        initialize()


    }

    private fun initialize() {
        initEvernoteSession()
        initTimber()
    }

    private fun initEvernoteSession() {
        //Set up the Evernote singleton session, use EvernoteSession.getInstance() later
        EvernoteSession.Builder(this)
            .setEvernoteService(EVERNOTE_SERVICE)
            .setSupportAppLinkedNotebooks(SUPPORT_APP_LINKED_NOTEBOOKS)
            .setForceAuthenticationInThirdPartyApp(true)
            .setLocale(Locale.JAPANESE)
            .build(CONSUMER_KEY, CONSUMER_SECRET)
            .asSingleton()
    }

    private fun initTimber() {
        Timber.plant(Timber.DebugTree())
    }

    companion object {
        lateinit var applicationContext: Context
        var isEvernoteLoggedIn = false
        var noteStoreClient: EvernoteNoteStoreClient? = null
        var evernoteUser: User? = null
        var evNotebook: Notebook? = null
        var mode: Mode? = null
    }
}