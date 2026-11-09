package com.insbeis.autotermin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class PowiadomienieReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val auto = intent.getStringExtra("auto") ?: "Auto"
        val typ = intent.getStringExtra("typ") ?: "Termin"
        val data = intent.getStringExtra("data") ?: ""

        // Tworzymy Intent otwierający MainActivity i przekazujący informację, że ma otworzyć zakładkę "Terminy"
        val mainIntent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("docelowa_zakladka", "Terminy")
        }

        // PendingIntent, który wykona się po kliknięciu w powiadomienie
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            mainIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Budujemy powiadomienie z przypnietym pendingIntent
        val channelId = "autotermin_channel"
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "AutoTermin Powiadomienia",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val przetłumaczonyTyp = translateTyp(typ, false)
        val linijkaAuto = auto
        val linijkaTermin = "Termin ważności: $data" // Etykieta i data w jednej linijce

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ikona)
            .setContentTitle(przetłumaczonyTyp)
            .setContentText("$linijkaAuto · $linijkaTermin")
            .setStyle(
                androidx.core.app.NotificationCompat.InboxStyle()
                    .addLine(linijkaAuto)       // 2. Linijka: Nazwa auta
                    .addLine(linijkaTermin)     // 3. Linijka: Termin ważności wraz z datą w tej samej linii
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
