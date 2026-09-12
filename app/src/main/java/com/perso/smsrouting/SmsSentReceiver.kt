package com.perso.smsrouting

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/**
 * Recoit le resultat reel d'un envoi.
 *
 * L'envoi par [SmsManager] est asynchrone : la methode rend la main avant que
 * l'operateur ait accepte le message. Sans ce retour, un echec de transmission
 * (absence de reseau, radio coupee, envoi refuse) resterait invisible et le
 * journal afficherait un succes qui n'a jamais eu lieu.
 *
 * Le recepteur n'est joignable que par les PendingIntent construits par
 * [Forwarder] : il est declare non exporte et sans filtre d'intention.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return

        val appContext = context.applicationContext
        val index = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val total = intent.getIntExtra(EXTRA_PART_COUNT, 1)

        if (resultCode == Activity.RESULT_OK) {
            // Une seule ligne de journal par message, ecrite a la derniere partie.
            if (index >= total - 1) {
                Prefs.appendLog(appContext, appContext.getString(R.string.log_send_confirmed, total))
            }
            return
        }

        val reason = appContext.getString(failureReason(resultCode))
        Prefs.appendLog(appContext, appContext.getString(R.string.log_send_failed, reason))
        Notifier.notifyFailure(appContext, reason)
    }

    private fun failureReason(code: Int): Int = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> R.string.send_error_no_service
        SmsManager.RESULT_ERROR_RADIO_OFF -> R.string.send_error_radio_off
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> R.string.send_error_limit
        else -> R.string.send_error_generic
    }

    companion object {
        const val ACTION_SMS_SENT = "com.perso.smsrouting.SMS_SENT"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
    }
}
