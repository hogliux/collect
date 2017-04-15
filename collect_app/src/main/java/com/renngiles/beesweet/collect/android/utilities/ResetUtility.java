/*
 * Copyright 2017 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.renngiles.beesweet.collect.android.utilities;

import android.content.Context;
import android.preference.PreferenceManager;

import com.renngiles.beesweet.collect.android.R;
import com.renngiles.beesweet.collect.android.application.Collect;
import com.renngiles.beesweet.collect.android.database.ItemsetDbAdapter;
import com.renngiles.beesweet.collect.android.provider.FormsProviderAPI;
import com.renngiles.beesweet.collect.android.provider.InstanceProviderAPI;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResetUtility {

    private List<Integer> mFailedResetActions;

    public List<Integer> reset(final Context context, List<Integer> resetActions) {

        mFailedResetActions = new ArrayList<>();
        mFailedResetActions.addAll(resetActions);

        for (int action : resetActions) {
            switch (action) {
                case ResetAction.RESET_PREFERENCES:
                    resetPreferences(context);
                    break;
                case ResetAction.RESET_INSTANCES:
                    resetInstances(context);
                    break;
                case ResetAction.RESET_FORMS:
                    resetForms(context);
                    break;
                case ResetAction.RESET_LAYERS:
                    if (deleteFolderContents(Collect.getODKPath (Collect.OFFLINE_LAYERS_ID))) {
                        mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_LAYERS));
                    }
                    break;
                case ResetAction.RESET_CACHE:
                    if (deleteFolderContents(Collect.getODKPath (Collect.CACHE_PATH_ID))) {
                        mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_CACHE));
                    }
                    break;
                case ResetAction.RESET_OSM_DROID:
                    if (deleteFolderContents(OpenStreetMapTileProviderConstants.TILE_PATH_BASE.getPath())) {
                        mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_OSM_DROID));
                    }
                    break;
            }
        }

        return mFailedResetActions;
    }

    private void resetPreferences(Context context) {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .clear()
                .apply();

        PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
        mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_PREFERENCES));
    }

    private void resetInstances(final Context context) {
        context.getContentResolver().delete(InstanceProviderAPI.InstanceColumns.CONTENT_URI, null, null);

        if (deleteFolderContents(Collect.getODKPath (Collect.INSTANCES_PATH_ID))) {
            mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_INSTANCES));
        }
    }

    private void resetForms(final Context context) {
        context.getContentResolver().delete(FormsProviderAPI.FormsColumns.CONTENT_URI, null, null);

        File itemsetDbFile = new File(Collect.getODKPath (Collect.METADATA_PATH_ID) + File.separator + ItemsetDbAdapter.DATABASE_NAME);

        if (deleteFolderContents(Collect.getODKPath (Collect.FORMS_PATH_ID)) && (!itemsetDbFile.exists() || itemsetDbFile.delete())) {
            mFailedResetActions.remove(mFailedResetActions.indexOf(ResetAction.RESET_FORMS));
        }
    }

    private boolean deleteFolderContents(String path) {
        boolean result = true;
        File file = new File(path);
        if (file.exists()) {
            File[] files = file.listFiles();

            for (File f : files) {
                result = deleteRecursive(f);
            }
        }
        return result;
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        return fileOrDirectory.delete();
    }

    public static class ResetAction {
        public static final int RESET_PREFERENCES = 0;
        public static final int RESET_INSTANCES = 1;
        public static final int RESET_FORMS = 2;
        public static final int RESET_LAYERS = 3;
        public static final int RESET_CACHE = 4;
        public static final int RESET_OSM_DROID = 5;
    }
}
