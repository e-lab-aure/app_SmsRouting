package com.perso.smsrouting

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Alerte l'utilisateur lorsqu'un reroutage echoue, pour eviter une perte silencieuse. */
object Notifier {

    private const val TAG = "Notifier"
    private const val CHANNEL_ID = "reroutage_erreurs"
    private const val NOTIFICATION_ID = 1

    /** Le message ne contient jamais le contenu du SMS, seulement la cause de l'echec. */
    fun notifyFailure(context: Context, reason: String) {
        // La permission n'est demandee a l'execution qu'a partir d'Android 13.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Forwarder.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notification_failure_title))
            .setContentText(reason)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            createChannel(context)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // La permission peut avoir ete revoquee entre la verification et
            // l'affichage. L'echec de reroutage reste visible dans le journal.
            Log.w(TAG, "Notification refusee", e)
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
