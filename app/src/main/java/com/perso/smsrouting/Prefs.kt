package com.perso.smsrouting

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stockage local de la configuration et du journal d'activite.
 *
 * Le fichier est prive a l'application et exclu des sauvegardes comme des
 * transferts d'appareil. Aucune donnee n'en sort, en dehors du SMS envoye au
 * numero configure.
 */
object Prefs {

    private const val FILE = "sms_routing_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_DESTINATION = "destination"
    private const val KEY_GROUP = "group_title"
    private const val KEY_INCLUDE_SENDER = "include_sender"
    private const val KEY_LOG = "log"
    private const val KEY_PERMISSION_ASKED = "permission_asked"

    /** Nombre maximum de lignes conservees dans le journal affiche par l'application. */
    private const val LOG_MAX_LINES = 30

    const val DEFAULT_GROUP = "Reroutage"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    }

    fun destination(context: Context): String =
        prefs(context).getString(KEY_DESTINATION, "").orEmpty()

    fun setDestination(context: Context, value: String) {
        prefs(context).edit { putString(KEY_DESTINATION, value.trim()) }
    }

    fun groupTitle(context: Context): String =
        prefs(context).getString(KEY_GROUP, DEFAULT_GROUP).orEmpty().ifBlank { DEFAULT_GROUP }

    fun setGroupTitle(context: Context, value: String) {
        prefs(context).edit { putString(KEY_GROUP, value.trim().ifBlank { DEFAULT_GROUP }) }
    }

    fun includeSender(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_SENDER, true)

    fun setIncludeSender(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_INCLUDE_SENDER, value) }
    }

    /** Vrai des que les permissions ont ete demandees au moins une fois. */
    fun permissionAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMISSION_ASKED, false)

    fun setPermissionAsked(context: Context) {
        prefs(context).edit { putBoolean(KEY_PERMISSION_ASKED, true) }
    }

    fun log(context: Context): String = prefs(context).getString(KEY_LOG, "").orEmpty()

    /**
     * Ajoute une ligne horodatee au journal.
     *
     * Le contenu des SMS n'y figure jamais, mais l'expediteur y apparait car il
     * est indispensable au diagnostic. Le journal est donc a traiter comme une
     * donnee personnelle, au meme titre que le repertoire.
     *
     * L'ecriture est synchronisee : l'ecran de configuration et les recepteurs
     * de SMS journalisent depuis des threads differents, et la sequence lecture
     * puis ecriture n'est pas atomique.
     */
    @Synchronized
    fun appendLog(context: Context, message: String) {
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date())
        val lines = ("$stamp  $message\n${log(context)}").lines().take(LOG_MAX_LINES)
        prefs(context).edit { putString(KEY_LOG, lines.joinToString("\n").trimEnd()) }
    }

    @Synchronized
    fun clearLog(context: Context) {
        prefs(context).edit { remove(KEY_LOG) }
    }
}
