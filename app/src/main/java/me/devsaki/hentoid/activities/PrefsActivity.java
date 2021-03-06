package me.devsaki.hentoid.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 20/05/2015.
 * Set up and present preferences.
 */
public class PrefsActivity extends BaseActivity {
    private static final String TAG = LogHelper.makeLogTag(PrefsActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new MyPreferenceFragment()).commit();
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        // Allow super to try and create a view first
        final View result = super.onCreateView(name, context, attrs);
        if (result != null) {
            return result;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If we're running pre-L, we need to 'inject' our tint aware Views in place of the
            // standard framework versions
            switch (name) {
                case "EditText":
                    return new AppCompatEditText(this, attrs);
                case "Spinner":
                    return new AppCompatSpinner(this, attrs);
                case "CheckBox":
                    return new AppCompatCheckBox(this, attrs);
                case "RadioButton":
                    return new AppCompatRadioButton(this, attrs);
                case "CheckedTextView":
                    return new AppCompatCheckedTextView(this, attrs);
                default: // do nothing
                    break;
            }
        }

        return null;
    }

    public static class MyPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            Preference recentVisibility = getPreferenceScreen()
                    .findPreference(ConstsPrefs.PREF_HIDE_RECENT);

            SharedPreferences prefs = HentoidApp.getSharedPrefs();
            boolean hideRecent = prefs.getBoolean(
                    ConstsPrefs.PREF_HIDE_RECENT, ConstsPrefs.PREF_HIDE_RECENT_DEFAULT);

            recentVisibility.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!newValue.equals(hideRecent)) {
                    Helper.toast(R.string.restart_needed);

                    return true;
                } else {
                    return true;
                }
            });

            Preference addNoMediaFile = getPreferenceScreen()
                    .findPreference(ConstsPrefs.PREF_ADD_NO_MEDIA_FILE);
            addNoMediaFile.setOnPreferenceClickListener(preference -> FileHelper.createNoMedia());

            Preference appLock = getPreferenceScreen().findPreference(ConstsPrefs.PREF_APP_LOCK);
            appLock.setOnPreferenceClickListener(preference -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.app_lock_pin_prefs);
                final EditText input = new EditText(getActivity());
                input.setGravity(Gravity.CENTER);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(input);
                builder.setOnKeyListener((dialog, keyCode, event) -> {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                            (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        saveKey(dialog, input);

                        return true;
                    }

                    return false;
                });
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> saveKey(dialog, input));
                builder.setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> dialog.cancel());
                builder.show();

                return true;
            });

            Preference mUpdateCheck = getPreferenceScreen()
                    .findPreference(ConstsPrefs.PREF_CHECK_UPDATE_MANUAL);
            mUpdateCheck.setOnPreferenceClickListener(preference -> {
                Helper.toast("Checking for updates...");
                new UpdateCheck().checkForUpdate(HentoidApp.getAppContext(), false, true,
                        new UpdateCheck.UpdateCheckCallback() {
                            @Override
                            public void noUpdateAvailable() {
                                LogHelper.d(TAG, "Update Check: No update.");
                            }

                            @Override
                            public void onUpdateAvailable() {
                                LogHelper.d(TAG, "Update Check: Update!");
                            }
                        });

                return true;
            });
        }

        private void saveKey(DialogInterface dialog, EditText input) {
            String lock = input.getText().toString();
            SharedPreferences.Editor editor = HentoidApp.getSharedPrefs().edit();
            editor.putString(ConstsPrefs.PREF_APP_LOCK, lock);
            editor.apply();
            if (lock.isEmpty()) {
                Helper.toast(getActivity(), R.string.app_lock_disabled);
            } else {
                Helper.toast(getActivity(), R.string.app_lock_enable);
            }
            dialog.cancel();
        }
    }
}
