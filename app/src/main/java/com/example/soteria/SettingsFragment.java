package com.example.soteria;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Set;

/** Renders the settings defined in res/xml/root_preferences.xml. */
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String CONTACT_ROW_PREFIX = "contact_";

    private PreferenceCategory contactsCategory;

    /** Android's contact picker. Returns one phone number; no READ_CONTACTS permission needed. */
    private final ActivityResultLauncher<Intent> contactPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    saveContact(result.getData().getData());
                }
            });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        contactsCategory = findPreference("trusted_contacts_category");

        Preference addContact = findPreference("add_contact");
        if (addContact != null) {
            addContact.setOnPreferenceClickListener(p -> {
                try {
                    contactPicker.launch(new Intent(Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI));
                } catch (android.content.ActivityNotFoundException e) {
                    android.widget.Toast.makeText(requireContext(), "No contacts app found",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        String packageUri = "package:" + requireContext().getPackageName();
        openOnClick("perm_a11y", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        openOnClick("perm_overlay",
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(packageUri)));
        openOnClick("perm_mic",
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(packageUri)));
        // Soteria posts call notifications; it has no notification listener, so open its own
        // notification settings (the listener screen could never show "Granted").
        Intent notificationSettings = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(packageUri));
        openOnClick("perm_notifications", notificationSettings);

        showContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh in case the user just granted something in system settings.
        updatePermissionStatus();
    }

    private void openOnClick(String key, Intent intent) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceClickListener(p -> {
                tryStart(intent);
                return true;
            });
        }
    }

    /** Some devices don't have every settings screen; don't crash if one is missing. */
    private void tryStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName())));
        }
    }

    // ---------- Trusted contacts ----------

    private void saveContact(@Nullable Uri contactUri) {
        if (contactUri == null) {
            return;
        }
        String[] columns = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor c = requireContext().getContentResolver()
                .query(contactUri, columns, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                Set<String> contacts = SettingsKeys.trustedContacts(requireContext());
                contacts.add(c.getString(0) + "|" + c.getString(1));
                SettingsKeys.setTrustedContacts(requireContext(), contacts);
                showContacts();
            }
        }
    }

    private void removeContact(String entry) {
        Set<String> contacts = SettingsKeys.trustedContacts(requireContext());
        contacts.remove(entry);
        SettingsKeys.setTrustedContacts(requireContext(), contacts);
        showContacts();
    }

    /** Rebuilds the contact rows under the "Trusted contacts" header. */
    private void showContacts() {
        if (contactsCategory == null) {
            return;
        }
        for (int i = contactsCategory.getPreferenceCount() - 1; i >= 0; i--) {
            Preference p = contactsCategory.getPreference(i);
            if (p.getKey() != null && p.getKey().startsWith(CONTACT_ROW_PREFIX)) {
                contactsCategory.removePreference(p);
            }
        }

        Context context = requireContext();
        String hint = getString(R.string.pref_contact_remove_hint);
        for (String entry : SettingsKeys.trustedContacts(context)) {
            String[] parts = entry.split("\\|", 2);
            Preference row = new Preference(context);
            row.setKey(CONTACT_ROW_PREFIX + entry);
            row.setTitle(parts[0]);
            row.setSummary(parts.length > 1 ? parts[1] + "  ·  " + hint : hint);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(p -> {
                removeContact(entry);
                return true;
            });
            contactsCategory.addPreference(row);
        }
    }

    // ---------- Permission status ----------

    private void updatePermissionStatus() {
        Context context = requireContext();
        boolean overlay = Settings.canDrawOverlays(context);
        boolean mic = ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();

        Preference a11y = findPreference("perm_a11y");
        if (a11y != null) {
            a11y.setSummary(SoteriaAccessibilityService.isEnabled(context)
                    ? R.string.pref_perm_granted : R.string.pref_perm_a11y_missing);
        }
        setStatus("perm_overlay", overlay);
        setStatus("perm_mic", mic);
        setStatus("perm_notifications", notifications);
    }

    private void setStatus(String key, boolean granted) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setSummary(granted ? R.string.pref_perm_granted : R.string.pref_perm_missing);
        }
    }
}
