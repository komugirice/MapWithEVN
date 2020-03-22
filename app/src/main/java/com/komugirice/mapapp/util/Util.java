package com.komugirice.mapapp.util;

import android.app.Activity;

import androidx.appcompat.app.AppCompatActivity;

import com.evernote.client.android.EvernoteSession;
import com.komugirice.mapapp.SplashActivity;

/**
 * @author rwondratschek
 */
public final class Util {

    private Util() {
        // no op
    }

    public static void logout(Activity activity) {
        EvernoteSession.getInstance().logOut();
        SplashActivity.Companion.start((AppCompatActivity)activity);
        activity.finish();
    }
}
