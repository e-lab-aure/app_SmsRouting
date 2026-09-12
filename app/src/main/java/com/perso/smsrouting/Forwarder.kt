package com.perso.smsrouting

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/** Decide si un SMS entrant doit etre reroute, puis realise l'envoi. */
object Forwarder {

    private const val TAG = "Forwarder"

    /**
     * Chaque partie envoyee a besoin d'un PendingIntent distinct, donc d'un code
     * de requete propre. Le compteur demarre a une valeur derivee de l'horloge
     * pour qu'un redemarrage du processus ne reutilise pas les codes d'envois
     * encore en vol.
     */
    private val requestCode = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    /**
     * Traite un SMS entrant. Appele depuis un thread de fond.
     *
     * Le message n'est reroute que si le reroutage est actif, qu'un numero de
     * destination est configure, que les permissions sont accordees et que
     * l'expediteur appartient au groupe de contacts surveille.
     */
    fun handle(context: Context, sender: String, body: String) {
        if (!Prefs.isEnabled(context)) return

        val destination = Prefs.destination(context)
        if (destination.isBlank()) {
            Prefs.appendLog(context, "Ignore: aucun numero de destination configure")
            return
        }

        if (!hasPermission(context, Manifest.permission.READ_CONTACTS) ||
            !hasPermission(context, Manifest.permission.SEND_SMS)
        ) {
            Prefs.appendLog(context, "Ignore: permissions manquantes")
            return
        }

        // Protection contre une boucle de reroutage si le destinataire figure
        // lui-meme dans le groupe surveille.
        if (PhoneNumbers.sameNumber(sender, destination)) {
            Prefs.appendLog(context, "Ignore: message provenant du numero de destination")
            return
        }

        val senderKey = PhoneNumbers.key(sender)
        if (senderKey.isEmpty()) return

        val groupTitle = Prefs.groupTitle(context)
        if (senderKey !in ContactGroups.numberKeysInGroup(context, groupTitle)) return

        send(context, destination, buildMessage(context, sender, body), sender)
    }

    private fun buildMessage(context: Context, sender: String, body: String): String {
        if (!Prefs.includeSender(context)) return body
        val name = Contacts.displayName(context, sender)
        val origin = if (name != null) "$name ($sender)" else sender
        return "De $origin :\n$body"
    }

    /**
     * Remet le message a la couche telephonie. Le resultat reel de la
     * transmission arrive plus tard dans [SmsSentReceiver] : le journal
     * distingue donc la remise de la confirmation d'envoi.
     */
    private fun send(context: Context, destination: String, text: String, sender: String) {
        try {
            val smsManager = smsManager(context)
            val parts = smsManager.divideMessage(text)
            val sentIntents = sentIntents(context, parts.size)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(destination, null, text, sentIntents.first(), null)
            }
            Prefs.appendLog(context, context.getString(R.string.log_handed_over, sender, parts.size))
        } catch (e: SecurityException) {
            Log.e(TAG, "Envoi refuse", e)
            reportFailure(context, context.getString(R.string.permission_sms_send))
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Parametres d'envoi invalides", e)
            reportFailure(context, context.getString(R.string.error_destination_invalid))
        } catch (e: RuntimeException) {
            Log.e(TAG, "Envoi impossible", e)
            reportFailure(context, context.getString(R.string.send_error_generic))
        }
    }

    private fun reportFailure(context: Context, reason: String) {
        Prefs.appendLog(context, context.getString(R.string.log_send_failed, reason))
        Notifier.notifyFailure(context, reason)
    }

    /** Un PendingIntent par partie, porteur de son rang pour n'en journaliser qu'une. */
    private fun sentIntents(context: Context, count: Int): ArrayList<PendingIntent> {
        val base = requestCode.getAndAdd(count)
        val intents = ArrayList<PendingIntent>(count)
        for (index in 0 until count) {
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(SmsSentReceiver.ACTION_SMS_SENT)
                .putExtra(SmsSentReceiver.EXTRA_PART_INDEX, index)
                .putExtra(SmsSentReceiver.EXTRA_PART_COUNT, count)
            intents.add(
                PendingIntent.getBroadcast(
                    context,
                    base + index,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        return intents
    }

    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
