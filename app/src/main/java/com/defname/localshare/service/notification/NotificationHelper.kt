// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.defname.localshare.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.defname.localshare.MainActivity
import com.defname.localshare.R
import com.defname.localshare.service.LocalShareService

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "local_share_server_channel"
        const val APPROVAL_CHANNEL_ID = "local_share_approval_channel"
        const val NOTIFICATION_ID = 1
        const val DEEP_LINK_URI = "localshare://transfers"
        const val ACTION_SHOW_TRANSFERS = "com.defname.localshare.ACTION_SHOW_TRANSFERS"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel für den dauerhaften Server-Dienst (niedrige Wichtigkeit, damit es nicht nervt)
        val serverChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(serverChannel)

        // Channel für Approvals (hohe Wichtigkeit für Heads-up / Banner oben)
        val approvalChannel = NotificationChannel(
            APPROVAL_CHANNEL_ID,
            context.getString(R.string.connection_request_notification_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(approvalChannel)
    }

    fun buildBaseNotification(serverIp: String, port: Int, isRunning: Boolean): Notification {
        val mainActivityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainActivityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(R.string.server_notification_text, serverIp, port))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setDeleteIntent(stopPendingIntent)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.service_notification_stop_server), stopPendingIntent)
            .build()
    }


    fun showApprovalNotification(clientIp: String, filename: String = "files") {
        val approveIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.APPROVE_IP
            putExtra("ip", clientIp)
        }
        val approvePending = PendingIntent.getService(context, clientIp.hashCode(), approveIntent, PendingIntent.FLAG_IMMUTABLE)

        val denyIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.DENY_IP
            putExtra("ip", clientIp)
        }
        val denyPending = PendingIntent.getService(context, clientIp.hashCode(), denyIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.connection_request_notification_title))
            .setContentText(context.getString(R.string.connection_request_notification_text, clientIp, filename))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL) // Wichtig für Heads-up (Ton/Vibration)
            .addAction(0, context.getString(R.string.connection_request_notification_accept), approvePending)
            .addAction(0, context.getString(R.string.connection_request_notification_deny), denyPending)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(clientIp.hashCode(), notification)
    }

    fun showUploadPermissionNotification(clientIp: String) {
        val approveIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.APPROVE_UPLOAD_PERMISSION
            putExtra("ip", clientIp)
        }
        val approvePending = PendingIntent.getService(context, clientIp.hashCode(), approveIntent, PendingIntent.FLAG_IMMUTABLE)

        val denyIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.DENY_UPLOAD_PERMISSION
            putExtra("ip", clientIp)
        }
        val denyPending = PendingIntent.getService(context, clientIp.hashCode(), denyIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle("Upload Permission Request")
            .setContentText("$clientIp asks for permission to upload files.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL) // Wichtig für Heads-up (Ton/Vibration)
            .addAction(0, context.getString(R.string.connection_request_notification_accept), approvePending)
            .addAction(0, context.getString(R.string.connection_request_notification_deny), denyPending)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(clientIp.hashCode(), notification)
    }

    fun showUploadRequestNotification(clientIp: String, fileCount: Int) {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_TRANSFERS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            "upload_request_$clientIp".hashCode(),
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle("Incoming Upload Request")
            .setContentText("$clientIp wants to send $fileCount file(s).")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify("upload_request_$clientIp".hashCode(), notification)
    }

    fun showWebRtcIncomingNotification(sessionId: String, remoteIp: String, filename: String, size: Long) {
        val acceptIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.ACTION_ACCEPT_WEBRTC
            putExtra("session_id", sessionId)
            putExtra("remote_ip", remoteIp)
        }
        val rejectIntent = Intent(context, LocalShareService::class.java).apply {
            action = LocalShareService.ACTION_REJECT_WEBRTC
            putExtra("session_id", sessionId)
            putExtra("remote_ip", remoteIp)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val accept = PendingIntent.getService(context, sessionId.hashCode(), acceptIntent, flags)
        val reject = PendingIntent.getService(context, sessionId.hashCode() xor 0x40000000, rejectIntent, flags)
        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle("Incoming direct file")
            .setContentText("$remoteIp wants to send $filename (${android.text.format.Formatter.formatFileSize(context, size)}).")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .addAction(0, "Accept", accept)
            .addAction(0, "Reject", reject)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(webRtcNotificationId(sessionId), notification)
    }

    fun updateWebRtcNotification(sessionId: String, message: String, progress: Long, total: Long) {
        val percent = if (total > 0L) ((progress.coerceAtMost(total) * 100L) / total).toInt() else 0
        val builder = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle("LocalShare direct transfer")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOnlyAlertOnce(true)
            .setOngoing(total > 0L && progress < total)
        if (total > 0L && progress < total) builder.setProgress(100, percent, false)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(webRtcNotificationId(sessionId), builder.build())
    }

    fun showWebRtcCompletedNotification(sessionId: String, filename: String, file: java.io.File?) {
        val openIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, sessionId.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val location = file?.absolutePath ?: "Downloads"
        val notification = NotificationCompat.Builder(context, APPROVAL_CHANNEL_ID)
            .setContentTitle("File received")
            .setContentText("$filename saved. $location")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$filename received successfully. Saved to: $location"))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(webRtcNotificationId(sessionId), notification)
    }

    fun cancelWebRtcNotification(sessionId: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(webRtcNotificationId(sessionId))
    }

    private fun webRtcNotificationId(sessionId: String) = 700000 + (sessionId.hashCode() and 0x000FFFFF)

}
