/*
 * Copyright (C) 2009 University of Washington
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

package com.renngiles.beesweet.collect.android.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;

import com.renngiles.beesweet.collect.android.R;
import com.renngiles.beesweet.collect.android.application.Collect;
import com.renngiles.beesweet.collect.android.listeners.FormDownloaderListener;
import com.renngiles.beesweet.collect.android.logic.FormDetails;
import com.renngiles.beesweet.collect.android.preferences.AdminPreferencesActivity;
import com.renngiles.beesweet.collect.android.preferences.PreferencesActivity;
import com.renngiles.beesweet.collect.android.provider.InstanceProviderAPI;
import com.renngiles.beesweet.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import com.renngiles.beesweet.collect.android.tasks.DownloadFormListTask;
import com.renngiles.beesweet.collect.android.tasks.DownloadFormsTask;
import com.renngiles.beesweet.collect.android.utilities.ApplicationConstants;
import com.renngiles.beesweet.collect.android.utilities.CompatibilityUtils;
import com.renngiles.beesweet.collect.android.listeners.FormListDownloaderListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends Activity implements FormListDownloaderListener, FormDownloaderListener {
    private static final String t = "MainMenuActivity";

    private static final int PASSWORD_DIALOG = 1;

    private static final int numberOfSecondsUntilFormDownloadAbort = 12;

    // menu options
    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int MENU_ADMIN = Menu.FIRST + 1;

    private static final String DIALOG_TITLE = "dialogtitle";
    private static final String DIALOG_MSG = "dialogmsg";
    private static final String DIALOG_SHOWING = "dialogshowing";

    // buttons
    private Button mEnterDataButton;
    private Button mManageFilesButton;
    private Button mSendDataButton;
    private Button mViewSentFormsButton;
    private Button mReviewDataButton;
    private Button mGetFormsButton;

    private View mReviewSpacer;
    private View mGetFormsSpacer;

    private AlertDialog mAlertDialog;
    private SharedPreferences mAdminPreferences;

    private int mCompletedCount;
    private int mSavedCount;
    private int mViewSentCount;

    private Cursor mFinalizedCursor;
    private Cursor mSavedCursor;
    private Cursor mViewSentCursor;

    private IncomingHandler mHandler = new IncomingHandler(this);
    private MyContentObserver mContentObserver = new MyContentObserver();

    private static boolean EXIT = true;

    private HashMap<String, FormDetails> mFormNamesAndURLs = new HashMap<String, FormDetails>();
    private ProgressDialog mProgressDialog;
    private DownloadFormListTask mDownloadFormListTask;
    private DownloadFormsTask mDownloadFormsTask;
    private String mAlertMsg;
    private boolean mAlertShowing = false;
    private String mAlertTitle;
    private static final int PROGRESS_DIALOG = 3;

    private static final String FORMNAME = "formname";
    private static final String FORMDETAIL_KEY = "formdetailkey";
    private static final String FORMID_DISPLAY = "formiddisplay";

    private static final String FORM_ID_KEY = "formid";
    private static final String FORM_VERSION_KEY = "formversion";

    private ArrayList<HashMap<String, String>> mFormList = new ArrayList<HashMap<String, String>>();

    // private static boolean DO_NOT_EXIT = false;

    private boolean downloadFormList() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

        if (ni == null || !ni.isConnected()) {
            Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } else {

            mFormNamesAndURLs = new HashMap<String, FormDetails>();
            if (mProgressDialog != null) {
                // This is needed because onPrepareDialog() is broken in 1.6.
                mProgressDialog.setMessage(getString(R.string.please_wait));
            }
            showDialog(PROGRESS_DIALOG);

            if (mDownloadFormListTask != null &&
                    mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
                return true; // we are already doing the download!!!
            } else if (mDownloadFormListTask != null) {
                mDownloadFormListTask.cancel(true);
            }

            mDownloadFormListTask = new DownloadFormListTask();
            mDownloadFormListTask.setDownloaderListener(this);

            mDownloadFormListTask.execute();

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            abortFormDownload();
                        }
                    },
                    numberOfSecondsUntilFormDownloadAbort * 1000
            );

            return true;
        }

        return false;
    }

    private void abortFormDownload()
    {
        synchronized (this) {
            if (mDownloadFormListTask != null)
            {
                mDownloadFormListTask.cancel (true);
            }
            else if (mDownloadFormsTask != null)
            {
                mDownloadFormsTask.cancel (true);
            }
        }
    }

    private void createAlertDialog(String title, String message) {
        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // ok
                        Collect.getInstance().getActivityLogger().logAction(this,
                                "createAlertDialog", "OK");
                        // just close the dialog
                        mAlertShowing = false;
                        startFillInForm();
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), quitListener);
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertMsg = message;
        mAlertTitle = title;
        mAlertShowing = true;
        mAlertDialog.show();
    }

    @Override
    public void formListDownloadingComplete(HashMap<String, FormDetails> result) {

        synchronized (this) {
            if (mDownloadFormListTask != null) {
                mDownloadFormListTask.setDownloaderListener(null);
                mDownloadFormListTask = null;
            }
        }

        if (result == null) {
            dismissDialog(PROGRESS_DIALOG);
        } else {

            if (result.containsKey(DownloadFormListTask.DL_ERROR_MSG)) {
                dismissDialog(PROGRESS_DIALOG);
                // Download failed
                String dialogMessage =
                        getString(R.string.list_failed_with_error,
                                result.get(DownloadFormListTask.DL_ERROR_MSG).errorStr);
                String dialogTitle = getString(R.string.load_remote_form_error);
                createAlertDialog(dialogTitle, dialogMessage);
            } else {
                // Everything worked. Clear the list and add the results.
                mFormNamesAndURLs = result;

                mFormList.clear();

                ArrayList<String> ids = new ArrayList<String>(mFormNamesAndURLs.keySet());
                for (int i = 0; i < result.size(); i++) {
                    String formDetailsKey = ids.get(i);
                    FormDetails details = mFormNamesAndURLs.get(formDetailsKey);
                    HashMap<String, String> item = new HashMap<String, String>();
                    item.put(FORMNAME, details.formName);
                    item.put(FORMID_DISPLAY,
                            ((details.formVersion == null) ? "" : (getString(R.string.version) + " "
                                    + details.formVersion + " ")) +
                                    "ID: " + details.formID);
                    item.put(FORMDETAIL_KEY, formDetailsKey);
                    item.put(FORM_ID_KEY, details.formID);
                    item.put(FORM_VERSION_KEY, details.formVersion);

                    // Insert the new form in alphabetical order.
                    if (mFormList.size() == 0) {
                        mFormList.add(item);
                    } else {
                        int j;
                        for (j = 0; j < mFormList.size(); j++) {
                            HashMap<String, String> compareMe = mFormList.get(j);
                            String name = compareMe.get(FORMNAME);
                            if (name.compareTo(mFormNamesAndURLs.get(ids.get(i)).formName) > 0) {
                                break;
                            }
                        }
                        mFormList.add(j, item);
                    }
                }

                downloadSelectedFiles();
                return;
            }
        }

        startFillInForm();
    }

    private void downloadSelectedFiles() {
        int totalCount = 0;
        ArrayList<FormDetails> filesToDownload = new ArrayList<FormDetails>();

        for (int i = 0; i < mFormList.size(); i++) {
            HashMap<String, String> item = mFormList.get (i);
            filesToDownload.add(mFormNamesAndURLs.get(item.get(FORMDETAIL_KEY)));
        }
        totalCount = filesToDownload.size();

        Collect.getInstance().getActivityLogger().logAction(this, "downloadSelectedFiles",
                Integer.toString(totalCount));

        if (totalCount > 0) {
            mDownloadFormsTask = new DownloadFormsTask();
            mDownloadFormsTask.setDownloaderListener(this);
            mDownloadFormsTask.execute(filesToDownload);
        } else {
            dismissDialog(PROGRESS_DIALOG);
            startFillInForm();
        }
    }

    public void formsDownloadingComplete(HashMap<FormDetails, String> result)
    {
        synchronized (this) {
            if (mDownloadFormsTask != null) {
                mDownloadFormsTask.setDownloaderListener(null);
                mDownloadFormsTask = null;
            }
        }

        if (mProgressDialog.isShowing()) {
            // should always be true here
            mProgressDialog.dismiss();
        }

        startFillInForm();
    }

    public void progressUpdate(String currentFile, int progress, int total)
    {
        mAlertMsg = getString(R.string.fetching_file, currentFile, String.valueOf(progress), String.valueOf(total));
        mProgressDialog.setMessage(mAlertMsg);
    }

    private void startFillInForm()
    {
        Collect.getInstance().getActivityLogger()
                .logAction(this, "fillBlankForm", "click");
        Intent i = new Intent(getApplicationContext(),
                FormChooserList.class);
        startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAlertMsg = getString(R.string.please_wait);

        // must be at the beginning of any activity that can be called from an
        // external intent
        Log.i(t, "Starting up, creating directories");
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        setContentView(R.layout.main_menu);

        {
            // dynamically construct the "ODK Collect vA.B" string
            TextView mainMenuMessageLabel = (TextView) findViewById(R.id.main_menu_header);
            mainMenuMessageLabel.setText(Collect.getInstance()
                    .getVersionedAppName());
        }

        setTitle(getString(R.string.main_menu));

        File f = new File(Collect.getODKPath (Collect.ODK_ROOT_ID) + "/collect.settings");
        if (f.exists()) {
            boolean success = loadSharedPreferencesFromFile(f);
            if (success) {
                Toast.makeText(this,
                        getString(R.string.settings_successfully_loaded_file_notification),
                        Toast.LENGTH_LONG).show();
                f.delete();
            } else {
                Toast.makeText(this,
                        getString(R.string.corrupt_settings_file_notification),
                        Toast.LENGTH_LONG).show();
            }
        }

        mReviewSpacer = findViewById(R.id.review_spacer);
        mGetFormsSpacer = findViewById(R.id.get_forms_spacer);

        mAdminPreferences = this.getSharedPreferences(
                AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

        // enter data button. expects a result.
        mEnterDataButton = (Button) findViewById(R.id.enter_data);
        mEnterDataButton.setText(getString(R.string.enter_data_button));
        mEnterDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (! downloadFormList())
                {
                    startFillInForm();
                }
            }
        });

        // review data button. expects a result.
        mReviewDataButton = (Button) findViewById(R.id.review_data);
        mReviewDataButton.setText(getString(R.string.review_data_button));
        mReviewDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger()
                        .logAction(this, ApplicationConstants.FormModes.EDIT_SAVED, "click");
                Intent i = new Intent(getApplicationContext(), InstanceChooserList.class);
                i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
                startActivity(i);
            }
        });

        // send data button. expects a result.
        mSendDataButton = (Button) findViewById(R.id.send_data);
        mSendDataButton.setText(getString(R.string.send_data_button));
        mSendDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger()
                        .logAction(this, "uploadForms", "click");
                Intent i = new Intent(getApplicationContext(),
                        InstanceUploaderList.class);
                startActivity(i);
            }
        });

        //View sent forms
        mViewSentFormsButton = (Button) findViewById(R.id.view_sent_forms);
        mViewSentFormsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger().logAction(this, ApplicationConstants.FormModes.VIEW_SENT, "click");
                Intent i = new Intent(getApplicationContext(), InstanceChooserList.class);
                i.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.VIEW_SENT);
                startActivity(i);
            }
        });

        // manage forms button. no result expected.
        mGetFormsButton = (Button) findViewById(R.id.get_forms);
        mGetFormsButton.setText(getString(R.string.get_forms));
        mGetFormsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger()
                        .logAction(this, "downloadBlankForms", "click");
                SharedPreferences sharedPreferences = PreferenceManager
                        .getDefaultSharedPreferences(MainMenuActivity.this);
                String protocol = sharedPreferences.getString(
                        PreferencesActivity.KEY_PROTOCOL, getString(R.string.protocol_odk_default));
                Intent i = null;
                if (protocol.equalsIgnoreCase(getString(R.string.protocol_google_sheets))) {
                    i = new Intent(getApplicationContext(),
                            GoogleDriveActivity.class);
                } else {
                    i = new Intent(getApplicationContext(),
                            FormDownloadList.class);
                }
                startActivity(i);
            }
        });

        // manage forms button. no result expected.
        mManageFilesButton = (Button) findViewById(R.id.manage_forms);
        mManageFilesButton.setText(getString(R.string.manage_files));
        mManageFilesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance().getActivityLogger()
                        .logAction(this, "deleteSavedForms", "click");
                Intent i = new Intent(getApplicationContext(),
                        FileManagerTabs.class);
                startActivity(i);
            }
        });

        if (savedInstanceState != null)
        {
            // to restore alert dialog.
            if (savedInstanceState.containsKey(DIALOG_TITLE)) {
                mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
            }
            if (savedInstanceState.containsKey(DIALOG_MSG)) {
                mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
            }
            if (savedInstanceState.containsKey(DIALOG_SHOWING)) {
                mAlertShowing = savedInstanceState.getBoolean(DIALOG_SHOWING);
            }
        }

        // count for finalized instances
        String selection = InstanceColumns.STATUS + "=? or "
                + InstanceColumns.STATUS + "=?";
        String selectionArgs[] = {InstanceProviderAPI.STATUS_COMPLETE,
                InstanceProviderAPI.STATUS_SUBMISSION_FAILED};

        try {
            mFinalizedCursor = managedQuery(InstanceColumns.CONTENT_URI, null,
                    selection, selectionArgs, null);
        } catch (Exception e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        if (mFinalizedCursor != null) {
            startManagingCursor(mFinalizedCursor);
        }
        mCompletedCount = mFinalizedCursor != null ? mFinalizedCursor.getCount() : 0;
        getContentResolver().registerContentObserver(InstanceColumns.CONTENT_URI, true,
                mContentObserver);
//		mFinalizedCursor.registerContentObserver(mContentObserver);

        // count for saved instances
        String selectionSaved = InstanceColumns.STATUS + "!=?";
        String selectionArgsSaved[] = {InstanceProviderAPI.STATUS_SUBMITTED};

        try {
            mSavedCursor = managedQuery(InstanceColumns.CONTENT_URI, null,
                    selectionSaved, selectionArgsSaved, null);
        } catch (Exception e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        if (mSavedCursor != null) {
            startManagingCursor(mSavedCursor);
        }
        mSavedCount = mSavedCursor != null ? mSavedCursor.getCount() : 0;

        //count for view sent form
        String selectionViewSent = InstanceColumns.STATUS + "=?";
        String selectionArgsViewSent[] = {InstanceProviderAPI.STATUS_SUBMITTED};
        try {
            mViewSentCursor = managedQuery(InstanceColumns.CONTENT_URI, null,
                    selectionViewSent, selectionArgsViewSent, null);
        } catch (Exception e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }
        if (mViewSentCursor != null) {
            startManagingCursor(mViewSentCursor);
        }
        mViewSentCount = mViewSentCursor != null ? mViewSentCursor.getCount() : 0;

        updateButtons();
        setupGoogleAnalytics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = this.getSharedPreferences(
                AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

        boolean edit = sharedPreferences.getBoolean(
                AdminPreferencesActivity.KEY_EDIT_SAVED, true);
        if (!edit) {
            mReviewDataButton.setVisibility(View.GONE);
            mReviewSpacer.setVisibility(View.GONE);
        } else {
            mReviewDataButton.setVisibility(View.VISIBLE);
            mReviewSpacer.setVisibility(View.VISIBLE);
        }

        boolean send = sharedPreferences.getBoolean(
                AdminPreferencesActivity.KEY_SEND_FINALIZED, true);
        if (!send) {
            mSendDataButton.setVisibility(View.GONE);
        } else {
            mSendDataButton.setVisibility(View.VISIBLE);
        }

        boolean view_sent = sharedPreferences.getBoolean(
                AdminPreferencesActivity.KEY_VIEW_SENT, true);
        if (!view_sent) {
            mViewSentFormsButton.setVisibility(View.GONE);
        } else {
            mViewSentFormsButton.setVisibility(View.VISIBLE);
        }

        boolean get_blank = sharedPreferences.getBoolean(
                AdminPreferencesActivity.KEY_GET_BLANK, true);
        if (!get_blank) {
            mGetFormsButton.setVisibility(View.GONE);
            mGetFormsSpacer.setVisibility(View.GONE);
        } else {
            mGetFormsButton.setVisibility(View.VISIBLE);
            mGetFormsSpacer.setVisibility(View.VISIBLE);
        }

        boolean delete_saved = sharedPreferences.getBoolean(
                AdminPreferencesActivity.KEY_DELETE_SAVED, true);
        if (!delete_saved) {
            mManageFilesButton.setVisibility(View.GONE);
        } else {
            mManageFilesButton.setVisibility(View.VISIBLE);
        }

        ((Collect) getApplication())
                .getDefaultTracker()
                .enableAutoActivityTracking(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Collect.getInstance().getActivityLogger().logOnStart(this);
    }

    @Override
    protected void onStop() {
        Collect.getInstance().getActivityLogger().logOnStop(this);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Collect.getInstance().getActivityLogger()
                .logAction(this, "onCreateOptionsMenu", "show");
        super.onCreateOptionsMenu(menu);

        CompatibilityUtils.setShowAsAction(
                menu.add(0, MENU_PREFERENCES, 0, R.string.general_preferences)
                        .setIcon(R.drawable.ic_menu_preferences),
                MenuItem.SHOW_AS_ACTION_NEVER);
        CompatibilityUtils.setShowAsAction(
                menu.add(0, MENU_ADMIN, 0, R.string.admin_preferences)
                        .setIcon(R.drawable.ic_menu_login),
                MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                Collect.getInstance()
                        .getActivityLogger()
                        .logAction(this, "onOptionsItemSelected",
                                "MENU_PREFERENCES");
                Intent ig = new Intent(this, PreferencesActivity.class);
                startActivity(ig);
                return true;
            case MENU_ADMIN:
                Collect.getInstance().getActivityLogger()
                        .logAction(this, "onOptionsItemSelected", "MENU_ADMIN");
                String pw = mAdminPreferences.getString(
                        AdminPreferencesActivity.KEY_ADMIN_PW, "");
                if ("".equalsIgnoreCase(pw)) {
                    Intent i = new Intent(getApplicationContext(),
                            AdminPreferencesActivity.class);
                    startActivity(i);
                } else {
                    showDialog(PASSWORD_DIALOG);
                    Collect.getInstance().getActivityLogger()
                            .logAction(this, "createAdminPasswordDialog", "show");
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        Collect.getInstance().getActivityLogger()
                .logAction(this, "createErrorDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Collect.getInstance()
                                .getActivityLogger()
                                .logAction(this, "createErrorDialog",
                                        shouldExit ? "exitApplication" : "OK");
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PASSWORD_DIALOG:

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final AlertDialog passwordDialog = builder.create();

                passwordDialog.setTitle(getString(R.string.enter_admin_password));
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                input.setTransformationMethod(PasswordTransformationMethod
                        .getInstance());
                passwordDialog.setView(input, 20, 10, 20, 10);

                passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                String value = input.getText().toString();
                                String pw = mAdminPreferences.getString(
                                        AdminPreferencesActivity.KEY_ADMIN_PW, "");
                                if (pw.compareTo(value) == 0) {
                                    Intent i = new Intent(getApplicationContext(),
                                            AdminPreferencesActivity.class);
                                    startActivity(i);
                                    input.setText("");
                                    passwordDialog.dismiss();
                                } else {
                                    Toast.makeText(
                                            MainMenuActivity.this,
                                            getString(R.string.admin_password_incorrect),
                                            Toast.LENGTH_SHORT).show();
                                    Collect.getInstance()
                                            .getActivityLogger()
                                            .logAction(this, "adminPasswordDialog",
                                                    "PASSWORD_INCORRECT");
                                }
                            }
                        });

                passwordDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance()
                                        .getActivityLogger()
                                        .logAction(this, "adminPasswordDialog",
                                                "cancel");
                                input.setText("");
                                return;
                            }
                        });

                passwordDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return passwordDialog;
            case PROGRESS_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this,
                        "onCreateDialog.PROGRESS_DIALOG", "show");
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this,
                                        "onCreateDialog.PROGRESS_DIALOG", "OK");
                                dialog.dismiss();
                                // we use the same progress dialog for both
                                // so whatever isn't null is running
                                if (mDownloadFormListTask != null) {
                                    mDownloadFormListTask.cancel(true);
                                }
                                if (mDownloadFormsTask != null) {
                                    mDownloadFormsTask.cancel(true);
                                }
                            }
                        };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mAlertMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.skipFormDownload), loadingButtonListener);
                return mProgressDialog;
        }
        return null;
    }

    // This flag must be set each time the app starts up
    private void setupGoogleAnalytics() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Collect
                .getInstance());
        boolean isAnalyticsEnabled = settings.getBoolean(PreferencesActivity.KEY_ANALYTICS, true);
        GoogleAnalytics googleAnalytics = GoogleAnalytics.getInstance(getApplicationContext());
        googleAnalytics.setAppOptOut(!isAnalyticsEnabled);
    }

    private void updateButtons() {
        if (mFinalizedCursor != null && !mFinalizedCursor.isClosed()) {
            mFinalizedCursor.requery();
            mCompletedCount = mFinalizedCursor.getCount();
            if (mCompletedCount > 0) {
                mSendDataButton.setText(getString(R.string.send_data_button, String.valueOf(mCompletedCount)));
            } else {
                mSendDataButton.setText(getString(R.string.send_data));
            }
        } else {
            mSendDataButton.setText(getString(R.string.send_data));
            Log.w(t,
                    "Cannot update \"Send Finalized\" button label since the database is closed. Perhaps the app is running in the background?");
        }

        if (mSavedCursor != null && !mSavedCursor.isClosed()) {
            mSavedCursor.requery();
            mSavedCount = mSavedCursor.getCount();
            if (mSavedCount > 0) {
                mReviewDataButton.setText(getString(R.string.review_data_button,
                        String.valueOf(mSavedCount)));
            } else {
                mReviewDataButton.setText(getString(R.string.review_data));
            }
        } else {
            mReviewDataButton.setText(getString(R.string.review_data));
            Log.w(t,
                    "Cannot update \"Edit Form\" button label since the database is closed. Perhaps the app is running in the background?");
        }

        if (mViewSentCursor != null && !mViewSentCursor.isClosed()) {
            mViewSentCursor.requery();
            mViewSentCount = mViewSentCursor.getCount();
            if (mViewSentCount > 0) {
                mViewSentFormsButton.setText(getString(R.string.view_sent_forms_button, String.valueOf(mViewSentCount)));
            } else {
                mViewSentFormsButton.setText(getString(R.string.view_sent_forms));
            }
        } else {
                       mViewSentFormsButton.setText(getString(R.string.view_sent_forms));
            Log.w(t,
                    "Cannot update \"View Sent\" button label since the database is closed. Perhaps the app is running in the background?");
        }
    }

    /**
     * notifies us that something changed
     */
    private class MyContentObserver extends ContentObserver {

        public MyContentObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHandler.sendEmptyMessage(0);
        }
    }

    /*
     * Used to prevent memory leaks
     */
    static class IncomingHandler extends Handler {
        private final WeakReference<MainMenuActivity> mTarget;

        IncomingHandler(MainMenuActivity target) {
            mTarget = new WeakReference<MainMenuActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            MainMenuActivity target = mTarget.get();
            if (target != null) {
                target.updateButtons();
            }
        }
    }

    private boolean loadSharedPreferencesFromFile(File src) {
        // this should probably be in a thread if it ever gets big
        boolean res = false;
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(
                    this).edit();
            prefEdit.clear();
            // first object is preferences
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    prefEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    prefEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    prefEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    prefEdit.putString(key, ((String) v));
                }
            }
            prefEdit.commit();

            // second object is admin options
            Editor adminEdit = getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES,
                    0).edit();
            adminEdit.clear();
            // first object is preferences
            Map<String, ?> adminEntries = (Map<String, ?>) input.readObject();
            for (Entry<String, ?> entry : adminEntries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    adminEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    adminEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    adminEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    adminEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    adminEdit.putString(key, ((String) v));
                }
            }
            adminEdit.commit();

            res = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return res;
    }

}
