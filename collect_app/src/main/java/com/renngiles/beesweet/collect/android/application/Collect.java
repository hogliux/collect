/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.renngiles.beesweet.collect.android.application;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDex;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import com.renngiles.beesweet.collect.android.R;
import com.renngiles.beesweet.collect.android.database.ActivityLogger;
import com.renngiles.beesweet.collect.android.external.ExternalDataManager;
import com.renngiles.beesweet.collect.android.logic.FormController;
import com.renngiles.beesweet.collect.android.logic.PropertyManager;
import com.renngiles.beesweet.collect.android.preferences.PreferencesActivity;
import com.renngiles.beesweet.collect.android.utilities.AgingCredentialsProvider;
import com.renngiles.beesweet.collect.android.utilities.AuthDialogUtility;
import com.renngiles.beesweet.collect.android.utilities.PRNGFixes;
import org.opendatakit.httpclientandroidlib.client.CookieStore;
import org.opendatakit.httpclientandroidlib.client.CredentialsProvider;
import org.opendatakit.httpclientandroidlib.client.protocol.HttpClientContext;
import org.opendatakit.httpclientandroidlib.impl.client.BasicCookieStore;
import org.opendatakit.httpclientandroidlib.protocol.BasicHttpContext;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

import java.io.File;

/**
 * Extends the Application class to implement
 *
 * @author carlhartung
 */
public class Collect extends Application {

    // Storage paths
    public static final String FORMS_PATH_STR = "forms";
    public static final String INSTANCES_PATH_STR = "instances";
    public static final String CACHE_PATH_STR = ".cache";
    public static final String METADATA_PATH_STR = "metadata";
    public static final String TMPFILE_PATH_STR = CACHE_PATH_STR + File.separator + "tmp.jpg";
    public static final String TMPDRAWFILE_PATH_STR = CACHE_PATH_STR + File.separator + "tmpDraw.jpg";
    public static final String TMPXML_PATH_STR = CACHE_PATH_STR + File.separator + "tmp.xml";
    public static final String LOG_PATH_STR = "log";
    public static final String OFFLINE_LAYERS_STR = "layers";

    public static final int ODK_ROOT_ID   = 0;
    public static final int FORMS_PATH_ID = 1;
    public static final int INSTANCES_PATH_ID = 2;
    public static final int CACHE_PATH_ID = 3;
    public static final int METADATA_PATH_ID = 4;
    public static final int TMPFILE_PATH_ID = 5;
    public static final int TMPDRAWFILE_PATH_ID = 6;
    public static final int TMPXML_PATH_ID = 7;
    public static final int LOG_PATH_ID = 8;
    public static final int OFFLINE_LAYERS_ID = 9;

    public static final String DEFAULT_FONTSIZE = "21";

    private static Collect singleton = null;

    static {
        PRNGFixes.apply();
    }

    // share all session cookies across all sessions...
    private CookieStore cookieStore = new BasicCookieStore();
    // retain credentials for 7 minutes...
    private CredentialsProvider credsProvider = new AgingCredentialsProvider(7 * 60 * 1000);
    private ActivityLogger mActivityLogger;
    private FormController mFormController = null;
    private ExternalDataManager externalDataManager;
    private Tracker mTracker;

    public static Collect getInstance() {
        return singleton;
    }

    public static String getODKRoot() {
        return getInstance().getApplicationInfo().dataDir + File.separator + "odk_beesweet";
    }

    public static String getODKPath (int pathID)
    {
        String p = getODKRoot();

        switch (pathID)
        {
            case ODK_ROOT_ID:
                return p;
            case FORMS_PATH_ID:
                return p + File.separator + FORMS_PATH_STR;
            case INSTANCES_PATH_ID:
                return p + File.separator + INSTANCES_PATH_STR;
            case CACHE_PATH_ID:
                return p + File.separator + CACHE_PATH_STR;
            case METADATA_PATH_ID:
                return p + File.separator + METADATA_PATH_STR;
            case TMPFILE_PATH_ID:
                return p + File.separator + TMPFILE_PATH_STR;
            case TMPDRAWFILE_PATH_ID:
                return p + File.separator + TMPDRAWFILE_PATH_STR;
            case TMPXML_PATH_ID:
                return p + File.separator + TMPXML_PATH_STR;
            case LOG_PATH_ID:
                return p + File.separator + LOG_PATH_STR;
            case OFFLINE_LAYERS_ID:
                return p + File.separator + OFFLINE_LAYERS_STR;
        }

        assert (false);
        return p;
    }

    public static int getQuestionFontsize() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Collect
                .getInstance());
        String question_font = settings.getString(PreferencesActivity.KEY_FONT_SIZE,
                Collect.DEFAULT_FONTSIZE);
        int questionFontsize = Integer.valueOf(question_font);
        return questionFontsize;
    }

    /**
     * Creates required directories on the SDCard (or other external storage)
     *
     * @throws RuntimeException if there is no SDCard or the directory exists as a non directory
     */
    public static void createODKDirs() throws RuntimeException {
        String cardstatus = Environment.getExternalStorageState();
        if (!cardstatus.equals(Environment.MEDIA_MOUNTED)) {
            throw new RuntimeException(
                    Collect.getInstance().getString(R.string.sdcard_unmounted, cardstatus));
        }

        int[] dirs = {
                ODK_ROOT_ID, FORMS_PATH_ID, INSTANCES_PATH_ID, CACHE_PATH_ID, METADATA_PATH_ID, OFFLINE_LAYERS_ID
        };

        for (int dirId : dirs) {
            String dirName = getODKPath (dirId);
            File dir = new File(dirName);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    RuntimeException e =
                            new RuntimeException("ODK reports :: Cannot create directory: "
                                    + dirName);
                    throw e;
                }
            } else {
                if (!dir.isDirectory()) {
                    RuntimeException e =
                            new RuntimeException("ODK reports :: " + dirName
                                    + " exists, but is not a directory");
                    throw e;
                }
            }
        }
    }

    /**
     * Predicate that tests whether a directory path might refer to an
     * ODK Tables instance data directory (e.g., for media attachments).
     */
    public static boolean isODKTablesInstanceDataDirectory(File directory) {
        /**
         * Special check to prevent deletion of files that
         * could be in use by ODK Tables.
         */
        String dirPath = directory.getAbsolutePath();
        String odkRoot = getODKPath(Collect.ODK_ROOT_ID);

        if (dirPath.startsWith(odkRoot)) {
            dirPath = dirPath.substring(odkRoot.length());
            String[] parts = dirPath.split(File.separator);
            // [appName, instances, tableId, instanceId ]
            if (parts.length == 4 && parts[1].equals("instances")) {
                return true;
            }
        }
        return false;
    }

    public ActivityLogger getActivityLogger() {
        return mActivityLogger;
    }

    public FormController getFormController() {
        return mFormController;
    }

    public void setFormController(FormController controller) {
        mFormController = controller;
    }

    public ExternalDataManager getExternalDataManager() {
        return externalDataManager;
    }

    public void setExternalDataManager(ExternalDataManager externalDataManager) {
        this.externalDataManager = externalDataManager;
    }

    public String getVersionedAppName() {
        String versionName = "";
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            versionName = " " + versionName.replaceFirst("-", "\n");
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return getString(R.string.app_name) + versionName;
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetworkInfo = manager.getActiveNetworkInfo();
        return currentNetworkInfo != null && currentNetworkInfo.isConnected();
    }

    /*
        Adds support for multidex support library. For more info check out the link below,
        https://developer.android.com/studio/build/multidex.html
    */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    /**
     * Construct and return a session context with shared cookieStore and credsProvider so a user
     * does not have to re-enter login information.
     */
    public synchronized HttpContext getHttpContext() {

        // context holds authentication state machine, so it cannot be
        // shared across independent activities.
        HttpContext localContext = new BasicHttpContext();

        localContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        localContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credsProvider);

        return localContext;
    }

    public CredentialsProvider getCredentialsProvider() {
        return credsProvider;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    @Override
    public void onCreate() {
        singleton = this;

        // // set up logging defaults for apache http component stack
        // Log log;
        // log = LogFactory.getLog("org.opendatakit.httpclientandroidlib");
        // log.enableError(true);
        // log.enableWarn(true);
        // log.enableInfo(true);
        // log.enableDebug(true);
        // log = LogFactory.getLog("org.opendatakit.httpclientandroidlib.wire");
        // log.enableError(true);
        // log.enableWarn(false);
        // log.enableInfo(false);
        // log.enableDebug(false);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        super.onCreate();

        PropertyManager mgr = new PropertyManager(this);

        FormController.initializeJavaRosa(mgr);

        mActivityLogger = new ActivityLogger(
                mgr.getSingularProperty(PropertyManager.DEVICE_ID_PROPERTY));

        AuthDialogUtility.setWebCredentialsFromPreferences(this);
    }

    /**
     * Gets the default {@link Tracker} for this {@link Application}.
     *
     * @return tracker
     */
    synchronized public Tracker getDefaultTracker() {
        if (mTracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            mTracker = analytics.newTracker(R.xml.global_tracker);
        }
        return mTracker;
    }

}
