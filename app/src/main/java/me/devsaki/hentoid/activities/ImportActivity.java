package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;

import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.dirpicker.events.OnDirCancelEvent;
import me.devsaki.hentoid.dirpicker.events.OnDirChosenEvent;
import me.devsaki.hentoid.dirpicker.events.OnSAFRequestEvent;
import me.devsaki.hentoid.dirpicker.events.OnTextViewClickedEvent;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.ui.DirChooserFragment;
import me.devsaki.hentoid.dirpicker.util.Convert;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.model.DoujinBuilder;
import me.devsaki.hentoid.model.URLBuilder;
import me.devsaki.hentoid.util.AttributeException;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory selection and Import Activity
 */
public class ImportActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(ImportActivity.class);

    private static final String CURRENT_DIR = "currentDir";
    private static final String PREV_DIR = "prevDir";
    private static final int KITKAT = Build.VERSION_CODES.KITKAT;
    private static final int LOLLIPOP = Build.VERSION_CODES.LOLLIPOP;
    private AlertDialog addDialog;
    private String result;
    private File currentRootDir;
    private File prevRootDir;
    private DirChooserFragment dirChooserFragment;
    private HentoidDB db;
    private ImageView instImage;
    private boolean restartFlag;
    private boolean prefInit;
    private final Handler importHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 0) {
                cleanUp(addDialog);
            }
            importHandler.removeCallbacksAndMessages(null);
            return false;
        }
    });
    private boolean defaultInit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout relativeLayout = new RelativeLayout(this, null, R.style.ImportTheme);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(relativeLayout, layoutParams);

        db = HentoidDB.getInstance(this);

        addDialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_dialog_warning)
                .setTitle(R.string.add_dialog)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .create();

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_APPLICATION_PREFERENCES)) {
                LogHelper.d(TAG, "Running from prefs screen.");
                prefInit = true;
            }
            if (intent.getAction().equals(Intent.ACTION_GET_CONTENT)) {
                LogHelper.d(TAG, "Importing default directory.");
                defaultInit = true;
            } else {
                LogHelper.d(TAG, "Intent: " + intent + "Action: " + intent.getAction());
            }
        }
        prepImport(savedInstanceState);
    }

    private void prepImport(Bundle savedState) {
        if (savedState == null) {
            result = ConstsImport.RESULT_EMPTY;
        } else {
            currentRootDir = (File) savedState.getSerializable(CURRENT_DIR);
            prevRootDir = (File) savedState.getSerializable(PREV_DIR);
            result = savedState.getString(ConstsImport.RESULT_KEY);
        }
        checkForDefaultDirectory();
    }

    private void checkForDefaultDirectory() {
        if (checkPermissions()) {
            String settingDir = FileHelper.getRoot();
            LogHelper.d(TAG, settingDir);

            File file;
            if (!settingDir.isEmpty()) {
                file = new File(settingDir);
            } else {
                file = new File(Environment.getExternalStorageDirectory() +
                        "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
            }

            if (file.exists() && file.isDirectory()) {
                currentRootDir = file;
            } else {
                currentRootDir = FileHelper.getDefaultDir(this, "");
                LogHelper.d(TAG, "Creating new storage directory.");
            }
            pickDownloadDirectory(currentRootDir);
        } else {
            LogHelper.d(TAG, "Do we have permission?");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(CURRENT_DIR, currentRootDir);
        outState.putSerializable(PREV_DIR, prevRootDir);
        outState.putString(ConstsImport.RESULT_KEY, result);
        super.onSaveInstanceState(outState);
    }

    // Validate permissions
    private boolean checkPermissions() {
        if (Helper.permissionsCheck(
                ImportActivity.this, ConstsImport.RQST_STORAGE_PERMISSION, true)) {
            LogHelper.d(TAG, "Storage permission allowed!");
            return true;
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                result = ConstsImport.PERMISSION_GRANTED;
                Intent returnIntent = new Intent();
                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                setResult(RESULT_OK, returnIntent);
                finish();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    result = ConstsImport.PERMISSION_DENIED;
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                } else {
                    result = ConstsImport.PERMISSION_DENIED_FORCED;
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                }
            }
        }
    }

    // Present Directory Picker
    private void pickDownloadDirectory(File dir) {
        File downloadDir = dir;
        if (FileHelper.isOnExtSdCard(dir) && !FileHelper.isWritable(dir)) {
            LogHelper.d(TAG, "Inaccessible: moving back to default directory.");
            downloadDir = currentRootDir = new File(Environment.getExternalStorageDirectory() +
                    "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
        }

        if (defaultInit) {
            prevRootDir = currentRootDir;
            initImport();
        } else {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            dirChooserFragment = DirChooserFragment.newInstance(downloadDir);
            dirChooserFragment.show(transaction, "DirectoryChooserFragment");
        }
    }

    @Override
    public void onBackPressed() {
        // Send result back to activity
        result = ConstsImport.RESULT_CANCELED;
        LogHelper.d(TAG, result);
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    @Subscribe
    public void onDirCancel(OnDirCancelEvent event) {
        onBackPressed();
    }

    @Subscribe
    public void onDirChosen(OnDirChosenEvent event) {
        File chosenDir = event.getDir();
        prevRootDir = currentRootDir;

        if (!currentRootDir.equals(chosenDir)) {
            restartFlag = true;
            currentRootDir = chosenDir;
        }
        dirChooserFragment.dismiss();
        initImport();
    }

    private void initImport() {
        LogHelper.d(TAG, "Clearing SAF");
        FileHelper.clearUri();

        if (Build.VERSION.SDK_INT >= KITKAT) {
            revokePermission();
        }

        LogHelper.d(TAG, "Storage Path: " + currentRootDir);
        importFolder(currentRootDir);
    }

    @Subscribe
    public void onOpFailed(OpFailedEvent event) {
        dirChooserFragment.dismiss();
        prepImport(null);
    }

    @Subscribe
    public void onManualInput(OnTextViewClickedEvent event) {
        if (event.getClickType()) {
            LogHelper.d(TAG, "Resetting directory back to default.");
            currentRootDir = new File(Environment.getExternalStorageDirectory() +
                    "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
            dirChooserFragment.dismiss();
            pickDownloadDirectory(currentRootDir);
        } else {
            final EditText text = new EditText(this);
            int paddingPx = Convert.dpToPixel(this, 16);
            text.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            text.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            text.setText(currentRootDir.toString());

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dir_path)
                    .setMessage(R.string.dir_path_inst)
                    .setView(text)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        Editable value = text.getText();
                        processManualInput(value);
                    }).setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private void processManualInput(@NonNull Editable value) {
        String path = String.valueOf(value);
        if (!("").equals(path)) {
            File file = new File(path);
            if (file.exists() && file.isDirectory() && file.canWrite()) {
                LogHelper.d(TAG, "Got a valid directory!");
                currentRootDir = file;
                dirChooserFragment.dismiss();
                pickDownloadDirectory(currentRootDir);
            } else {
                dirChooserFragment.dismiss();
                prepImport(null);
            }
        }
        LogHelper.d(TAG, path);
    }

    @Subscribe
    public void onSAFRequest(OnSAFRequestEvent event) {
        String[] externalDirs = FileHelper.getExtSdCardPaths();
        List<File> writeableDirs = new ArrayList<>();
        if (externalDirs.length > 0) {
            LogHelper.d(TAG, "External Directory(ies): " + Arrays.toString(externalDirs));
            for (String externalDir : externalDirs) {
                File file = new File(externalDir);
                LogHelper.d(TAG, "Is " + externalDir + " write-able? " +
                        FileHelper.isWritable(file));
                if (FileHelper.isWritable(file)) {
                    writeableDirs.add(file);
                }
            }
        }
        resolveDirs(externalDirs, writeableDirs);
    }

    private void resolveDirs(String[] externalDirs, List<File> writeableDirs) {
        if (writeableDirs.isEmpty()) {
            LogHelper.d(TAG, "Received no write-able external directories.");
            if (Helper.isAtLeastAPI(LOLLIPOP)) {
                if (externalDirs.length > 0) {
                    Helper.toast("Attempting SAF");
                    requestWritePermission();
                } else {
                    noSDSupport();
                }
            } else {
                noSDSupport();
            }
        } else {
            if (writeableDirs.size() == 1) {
                // If we get exactly one write-able path returned, attempt to make use of it
                String sdDir = writeableDirs.get(0) + "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/";
                if (FileHelper.validateFolder(sdDir)) {
                    LogHelper.d(TAG, "Got access to SD Card.");
                    currentRootDir = new File(sdDir);
                    dirChooserFragment.dismiss();
                    pickDownloadDirectory(currentRootDir);
                } else {
                    if (Build.VERSION.SDK_INT == KITKAT) {
                        LogHelper.d(TAG, "Unable to write to SD Card.");
                        showKitkatRationale();
                    } else if (Helper.isAtLeastAPI(LOLLIPOP)) {
                        PackageManager manager = this.getPackageManager();
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        List<ResolveInfo> handlers = manager.queryIntentActivities(intent, 0);
                        if (handlers != null && handlers.size() > 0) {
                            LogHelper.d(TAG, "Device should be able to handle the SAF request");
                            Helper.toast("Attempting SAF");
                            requestWritePermission();
                        } else {
                            LogHelper.d(TAG, "No apps can handle the requested intent.");
                        }
                    } else {
                        noSDSupport();
                    }
                }
            } else {
                LogHelper.d(TAG, "We got a fancy device here.");
                LogHelper.d(TAG, "Available storage locations: " + writeableDirs);
                noSDSupport();
            }
        }
    }

    private void showKitkatRationale() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.kitkat_rationale)
                .setTitle("Error!")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void noSDSupport() {
        LogHelper.d(TAG, "No write-able directories :(");
        Helper.toast(R.string.no_sd_support);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        attachInstImage();
    }

    private void attachInstImage() {
        // A list of known devices can be used here to present instructions relevant to that device
        if (instImage != null) {
            instImage.setImageDrawable(ContextCompat.getDrawable(ImportActivity.this,
                    R.drawable.bg_sd_instructions));
        }
    }

    @RequiresApi(api = LOLLIPOP)
    private void requestWritePermission() {
        runOnUiThread(() -> {
            instImage = new ImageView(ImportActivity.this);
            attachInstImage();

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(ImportActivity.this)
                            .setTitle("Requesting Write Permissions")
                            .setView(instImage)
                            .setPositiveButton(android.R.string.ok,
                                    (dialogInterface, i) -> {
                                        dialogInterface.dismiss();
                                        newSAFIntent();
                                    });
            final AlertDialog dialog = builder.create();
            instImage.setOnClickListener(v -> {
                dialog.dismiss();
                newSAFIntent();
            });
            dialog.show();
        });
    }

    @RequiresApi(api = LOLLIPOP)
    private void newSAFIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.M)) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        startActivityForResult(intent, ConstsImport.RQST_STORAGE_PERMISSION);
    }

    @RequiresApi(api = KITKAT)
    private void revokePermission() {
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            getContentResolver().releasePersistableUriPermission(p.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        if (getContentResolver().getPersistedUriPermissions().size() == 0) {
            LogHelper.d(TAG, "Permissions revoked successfully.");
        } else {
            LogHelper.d(TAG, "Permissions failed to be revoked.");
        }
    }

    @RequiresApi(api = KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ConstsImport.RQST_STORAGE_PERMISSION && resultCode == RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();

            // Persist URI in shared preference so that you can use it later
            FileHelper.saveUri(treeUri);

            // Persist access permissions
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            dirChooserFragment.dismiss();

            if (FileHelper.getExtSdCardPaths().length > 0) {
                String[] paths = FileHelper.getExtSdCardPaths();
                File folder = new File(paths[0] + "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
                LogHelper.d(TAG, "Directory created successfully: " +
                        FileHelper.createDirectory(folder));

                importFolder(folder);
            }
        }
    }

    private void importFolder(File folder) {
        if (!FileHelper.validateFolder(folder.getAbsolutePath(), true)) {
            prepImport(null);
            return;
        }

        List<File> downloadDirs = new ArrayList<>();
        for (Site s : Site.values()) {
            downloadDirs.add(FileHelper.getSiteDownloadDir(this, s));
        }

        List<File> files = new ArrayList<>();
        for (File downloadDir : downloadDirs) {
            File[] contentFiles = downloadDir.listFiles();
            if (contentFiles != null)
                files.addAll(Arrays.asList(contentFiles));
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_dialog_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.contents_detected)
                .setPositiveButton(android.R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            // Prior Library found, drop and recreate db
                            cleanUpDB();
                            // Send results to scan
                            Helper.executeAsyncTask(new ImportAsyncTask());
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog12, which) -> {
                            dialog12.dismiss();
                            // Prior Library found, but user chose to cancel
                            restartFlag = false;
                            if (prevRootDir != null) {
                                currentRootDir = prevRootDir;
                            }
                            if (currentRootDir != null) {
                                FileHelper.validateFolder(currentRootDir.getAbsolutePath());
                            }
                            LogHelper.d(TAG, "Restart needed: " + false);

                            result = ConstsImport.EXISTING_LIBRARY_FOUND;
                            Intent returnIntent = new Intent();
                            returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                            setResult(RESULT_CANCELED, returnIntent);
                            finish();
                        })
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        if (files.size() > 0) {
            dialog.show();
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            cleanUpDB();
            result = ConstsImport.NEW_LIBRARY_CREATED;

            Handler handler = new Handler();

            LogHelper.d(TAG, result);

            handler.postDelayed(() -> {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                setResult(RESULT_OK, returnIntent);
                finish();
            }, 100);
        }
    }

    private void cleanUpDB() {
        LogHelper.d(TAG, "Cleaning up DB.");
        Context context = HentoidApp.getAppContext();
        context.deleteDatabase(Consts.DATABASE_NAME);
    }

    private void cleanUp(AlertDialog mAddDialog) {
        if (mAddDialog != null) {
            mAddDialog.dismiss();
        }
        LogHelper.d(TAG, "Restart needed: " + restartFlag);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_OK, returnIntent);
        finish();

        if (restartFlag && prefInit) {
            Helper.doRestart(this);
        }
    }

    private void finishImport(final List<Content> contents) {
        if (contents != null && contents.size() > 0) {
            LogHelper.d(TAG, "Adding contents to db.");
            addDialog.show();

            Thread thread = new Thread() {
                @Override
                public void run() {
                    // Grab all parsed content and add to database
                    db.insertContents(contents.toArray(new Content[contents.size()]));
                    importHandler.sendEmptyMessage(0);
                }
            };
            thread.start();

            result = ConstsImport.EXISTING_LIBRARY_IMPORTED;
        } else {
            result = ConstsImport.NEW_LIBRARY_CREATED;
            cleanUp(addDialog);
        }
    }

    private List<Attribute> from(List<URLBuilder> urlBuilders, AttributeType type) {
        List<Attribute> attributes = null;
        if (urlBuilders == null) {
            return null;
        }
        if (urlBuilders.size() > 0) {
            attributes = new ArrayList<>();
            for (URLBuilder urlBuilder : urlBuilders) {
                Attribute attribute = from(urlBuilder, type);
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        }

        return attributes;
    }

    private Attribute from(URLBuilder urlBuilder, AttributeType type) {
        if (urlBuilder == null) {
            return null;
        }
        try {
            if (urlBuilder.getDescription() == null) {
                throw new AttributeException("Problems loading attribute v2.");
            }

            return new Attribute()
                    .setName(urlBuilder.getDescription())
                    .setUrl(urlBuilder.getId())
                    .setType(type);
        } catch (Exception e) {
            LogHelper.e(TAG, e, "Parsing URL to attribute");
            return null;
        }
    }

    private class ImportAsyncTask extends AsyncTask<Integer, String, List<Content>> {
        private MaterialDialog mImportDialog;
        private List<File> downloadDirs;
        private List<File> files;
        private List<Content> contents;
        private int currentPercent;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            final MaterialDialog mScanDialog =
                    new MaterialDialog.Builder(ImportActivity.this)
                            .title(R.string.import_dialog)
                            .content(R.string.please_wait)
                            .contentGravity(GravityEnum.CENTER)
                            .progress(false, 100, false)
                            .cancelable(false)
                            .showListener(dialogInterface -> mImportDialog =
                                    (MaterialDialog) dialogInterface).build();
            if (mScanDialog.getWindow() != null) {
                mScanDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

            downloadDirs = new ArrayList<>();
            for (Site site : Site.values()) {
                // Grab all folders in site folders in storage directory
                downloadDirs.add(FileHelper.getSiteDownloadDir(ImportActivity.this, site));
            }

            files = new ArrayList<>();
            for (File downloadDir : downloadDirs) {
                // Grab all files in downloadDirs
                files.addAll(Arrays.asList(downloadDir.listFiles()));
            }

            mScanDialog.show();
        }

        @Override
        protected void onPostExecute(List<Content> contents) {
            mImportDialog.dismiss();
            finishImport(contents);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (currentPercent == 100) {
                mImportDialog.setContent(R.string.adding_to_db);
            } else {
                mImportDialog.setContent(R.string.scanning_files);
            }
            mImportDialog.setProgress(currentPercent);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected List<Content> doInBackground(Integer... params) {
            int processed = 0;
            if (files.size() > 0) {
                contents = new ArrayList<>();
                for (File file : files) {
                    if (file.isDirectory()) {
                        // (v2) JSON file format
                        File json = new File(file, Consts.JSON_FILE_NAME_V2);
                        if (json.exists()) {
                            importJsonV2(json);
                        } else {
                            // (v1) JSON file format
                            json = new File(file, Consts.JSON_FILE_NAME);
                            if (json.exists()) {
                                importJsonV1(json, file);
                            } else {
                                // (old) JSON file format (legacy and/or FAKKUDroid App)
                                json = new File(file, Consts.OLD_JSON_FILE_NAME);
                                Date importedDate = new Date();
                                if (json.exists()) {
                                    importJsonLegacy(json, file, importedDate);
                                }
                            }
                        }
                        publishProgress();
                    }
                    processed++;
                    currentPercent = (int) (processed * 100.0 / files.size());
                }
            }

            return contents;
        }

        private void importJsonLegacy(File json, File file, Date importedDate) {
            try {
                DoujinBuilder doujinBuilder =
                        JsonHelper.jsonToObject(json, DoujinBuilder.class);
                //noinspection deprecation
                ContentV1 content = new ContentV1();
                content.setUrl(doujinBuilder.getId());
                content.setHtmlDescription(doujinBuilder.getDescription());
                content.setTitle(doujinBuilder.getTitle());
                content.setSeries(from(doujinBuilder.getSeries(),
                        AttributeType.SERIE));
                Attribute artist = from(doujinBuilder.getArtist(),
                        AttributeType.ARTIST);
                List<Attribute> artists = null;
                if (artist != null) {
                    artists = new ArrayList<>(1);
                    artists.add(artist);
                }
                content.setArtists(artists);
                content.setCoverImageUrl(doujinBuilder.getUrlImageTitle());
                content.setQtyPages(doujinBuilder.getQtyPages());
                Attribute translator = from(doujinBuilder.getTranslator(),
                        AttributeType.TRANSLATOR);
                List<Attribute> translators = null;
                if (translator != null) {
                    translators = new ArrayList<>(1);
                    translators.add(translator);
                }
                content.setTranslators(translators);
                content.setTags(from(doujinBuilder.getLstTags(),
                        AttributeType.TAG));
                content.setLanguage(from(doujinBuilder.getLanguage(),
                        AttributeType.LANGUAGE));

                content.setMigratedStatus();
                content.setDownloadDate(importedDate.getTime());
                Content contentV2 = content.toV2Content();
                try {
                    JsonHelper.saveJson(contentV2, file);
                } catch (IOException e) {
                    LogHelper.e(TAG, e,
                            "Error converting JSON (old) to JSON (v2): " + content.getTitle());
                }
                contents.add(contentV2);
            } catch (Exception e) {
                LogHelper.e(TAG, e, "Error reading JSON (old) file");
            }
        }

        private void importJsonV1(File json, File file) {
            try {
                //noinspection deprecation
                ContentV1 content = JsonHelper.jsonToObject(json, ContentV1.class);
                if (content.getStatus() != StatusContent.DOWNLOADED
                        && content.getStatus() != StatusContent.ERROR) {
                    content.setMigratedStatus();
                }
                Content contentV2 = content.toV2Content();
                try {
                    JsonHelper.saveJson(contentV2, file);
                } catch (IOException e) {
                    LogHelper.e(TAG, e, "Error converting JSON (v1) to JSON (v2): "
                            + content.getTitle());
                }
                contents.add(contentV2);
            } catch (Exception e) {
                LogHelper.e(TAG, e, "Error reading JSON (v1) file");
            }
        }

        private void importJsonV2(File json) {
            try {
                Content content = JsonHelper.jsonToObject(json, Content.class);
                if (content.getStatus() != StatusContent.DOWNLOADED
                        && content.getStatus() != StatusContent.ERROR) {
                    content.setStatus(StatusContent.MIGRATED);
                }
                contents.add(content);
            } catch (Exception e) {
                LogHelper.e(TAG, e, "Error reading JSON (v2) file");
            }
        }
    }
}
