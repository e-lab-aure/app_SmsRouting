package com.perso.smsrouting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

/**
 * Recoit les SMS entrants. Declare dans le manifeste, il reste actif meme
 * lorsque l'application n'est pas ouverte.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        // Un SMS long arrive decoupe en plusieurs parties a rassembler.
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isEmpty()) return

        // La lecture des contacts et l'envoi ne doivent pas bloquer le thread
        // principal: goAsync maintient le recepteur vivant pendant le traitement.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        EXECUTOR.execute {
            try {
                Forwarder.handle(appContext, sender, body)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Traitement du SMS entrant impossible", e)
                Prefs.appendLog(appContext, "Echec: erreur interne lors du traitement")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
