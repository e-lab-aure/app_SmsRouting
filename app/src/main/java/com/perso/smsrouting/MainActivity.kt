package com.perso.smsrouting

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.perso.smsrouting.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    private val contactLauncher =
        registerForActivityResult(PickPhoneNumber()) { uri ->
            if (uri != null) readPickedNumber(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.destinationLayout.setEndIconOnClickListener { pickContact() }
        binding.save.setOnClickListener { saveConfiguration() }
        binding.permissions.setOnClickListener { requestMissingPermissions() }
        binding.group.setOnItemClickListener { _, _, _, _ -> updateGroupSummary() }
        binding.battery.setOnClickListener { openBatterySettings() }
        binding.clearLog.setOnClickListener {
            Prefs.clearLog(this)
            refreshLog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadConfiguration()
        refreshStatus()
        refreshLog()
        loadGroupList()
        updateGroupSummary()
        applyReceiverState(Prefs.isEnabled(this))
    }

    override fun onDestroy() {
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    /** L'affichage bord a bord impose de decaler manuellement le contenu des barres systeme. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scroll) { view, windowInsets ->
            val bars: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun loadConfiguration() {
        binding.destination.setText(Prefs.destination(this))
        // setText(texte, false) evite que la saisie filtre la liste deroulante.
        binding.group.setText(Prefs.groupTitle(this), false)
        binding.includeSender.isChecked = Prefs.includeSender(this)
        binding.enabled.isChecked = Prefs.isEnabled(this)
    }

    private fun saveConfiguration() {
        val destination = binding.destination.text?.toString()?.trim().orEmpty()
        val group = selectedGroup()
        val enable = binding.enabled.isChecked

        if (enable) {
            if (destination.isEmpty()) {
                showMessage(getString(R.string.error_destination_required))
                return
            }
            if (!isValidPhoneNumber(destination)) {
                showMessage(getString(R.string.error_destination_invalid))
                return
            }
            if (missingPermissions().isNotEmpty()) {
                showMessage(getString(R.string.error_permissions_required))
                return
            }
        }

        Prefs.setDestination(this, destination)
        Prefs.setGroupTitle(this, group)
        Prefs.setIncludeSender(this, binding.includeSender.isChecked)
        Prefs.setEnabled(this, enable)
        applyReceiverState(enable)

        binding.group.setText(group, false)
        showMessage(getString(R.string.saved))
        refreshStatus()
    }

    /** Accepte les formats nationaux et internationaux, separateurs inclus. */
    private fun isValidPhoneNumber(value: String): Boolean {
        if (!value.all { it.isDigit() || it in "+ .-()" }) return false
        val digits = value.count { it.isDigit() }
        return digits in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
    }

    private fun pickContact() {
        try {
            contactLauncher.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.error_contacts_app_unavailable))
        }
    }

    /**
     * Lit le numero choisi dans le repertoire. L'URI renvoye par le selecteur
     * donne un acces temporaire a cette seule ligne, sans lecture du repertoire
     * complet.
     */
    private fun readPickedNumber(uri: Uri) {
        backgroundExecutor.execute {
            val number = queryPickedNumber(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (number.isNullOrBlank()) {
                    showMessage(getString(R.string.error_contact_no_number))
                } else {
                    binding.destination.setText(number)
                }
            }
        }
    }

    private fun queryPickedNumber(uri: Uri): String? = try {
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) normalizeForInput(cursor.getString(0)) else null
        }
    } catch (e: SecurityException) {
        null
    }

    /**
     * Le repertoire peut contenir des separateurs varies, y compris des espaces
     * insecables. Seuls les chiffres et l'indicatif sont conserves pour que la
     * valeur passe la validation du champ.
     */
    private fun normalizeForInput(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        return cleaned.ifBlank { null }
    }

    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            refreshStatus()
            return
        }

        // Apres un refus definitif, Android n'affiche plus la boite de dialogue.
        // Sans cette redirection, le bouton resterait sans effet visible.
        val refusedForGood = Prefs.permissionAsked(this) &&
            missing.none { shouldShowRequestPermissionRationale(it) }
        if (refusedForGood) {
            showMessage(getString(R.string.error_permission_blocked))
            openAppSettings()
            return
        }

        Prefs.setPermissionAsked(this)
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun openAppSettings() {
        startSettings(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    /**
     * Desactive le recepteur de SMS quand le reroutage est coupe : le systeme
     * cesse alors de reveiller l'application a chaque message recu.
     */
    private fun applyReceiverState(enabled: Boolean) {
        val component = ComponentName(this, SmsReceiver::class.java)
        val target = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) == target) return
        packageManager.setComponentEnabledSetting(component, target, PackageManager.DONT_KILL_APP)
    }

    private fun missingPermissions(): List<String> = requiredPermissions().filterNot {
        Forwarder.hasPermission(this, it)
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshStatus() {
        val missing = missingPermissions().map { permissionLabel(it) }
        binding.status.text = when {
            missing.isNotEmpty() -> getString(R.string.status_missing, missing.joinToString(", "))
            !isIgnoringBatteryOptimizations() -> getString(R.string.status_battery_warning)
            else -> getString(R.string.status_ready)
        }
        binding.permissions.isEnabled = missing.isNotEmpty()
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.RECEIVE_SMS -> getString(R.string.permission_sms_receive)
        Manifest.permission.SEND_SMS -> getString(R.string.permission_sms_send)
        Manifest.permission.READ_CONTACTS -> getString(R.string.permission_contacts)
        else -> getString(R.string.permission_notifications)
    }

    private fun refreshLog() {
        val log = Prefs.log(this)
        binding.log.text = log.ifBlank { getString(R.string.log_empty) }
    }

    /** Alimente la liste deroulante avec les groupes presents sur l'appareil. */
    private fun loadGroupList() {
        if (!Forwarder.hasPermission(this, Manifest.permission.READ_CONTACTS)) return
        backgroundExecutor.execute {
            val titles = ContactGroups.listGroupTitles(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.group.setSimpleItems(titles.toTypedArray())
                if (titles.isEmpty()) {
                    binding.groupLayout.helperText = getString(R.string.group_none_available)
                }
            }
        }
    }

    /**
     * Affiche le nombre d'expediteurs effectivement surveilles pour le groupe
     * choisi. La lecture du repertoire peut etre longue: elle est faite hors du
     * thread principal.
     */
    private fun updateGroupSummary() {
        if (!Forwarder.hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            binding.groupLayout.helperText = getString(R.string.group_permission_needed)
            return
        }
        val group = selectedGroup()
        backgroundExecutor.execute {
            val count = ContactGroups.numberKeysInGroup(applicationContext, group).size
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.groupLayout.helperText =
                    if (count > 0) resources.getQuantityString(R.plurals.group_watching, count, count)
                    else getString(R.string.group_watching_none)
            }
        }
    }

    private fun selectedGroup(): String =
        binding.group.text?.toString()?.trim().orEmpty().ifBlank { Prefs.DEFAULT_GROUP }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun openBatterySettings() {
        startSettings(
            if (isIgnoringBatteryOptimizations()) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                @Suppress("BatteryLife")
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:$packageName".toUri()
                )
            }
        )
    }

    /** Tous les ecrans de reglages ne sont pas garantis presents selon la surcouche. */
    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.error_settings_unavailable))
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Selection d'un numero precis et non d'un contact entier, utile si le contact en a plusieurs. */
    private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {

        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    private companion object {
        const val MIN_PHONE_DIGITS = 6
        const val MAX_PHONE_DIGITS = 15
    }
}
