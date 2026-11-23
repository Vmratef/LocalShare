package com.defname.localshare.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.defname.localshare.data.RemoteCommunicationRepository
import com.defname.localshare.service.notification.NotificationHelper
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Native Android endpoint for the browser's existing LocalShare WebRTC protocol.
 * File payloads travel only over the peer data channel; the HTTP server carries signaling only.
 */
class DirectWebRtcReceiver(
    private val context: Context,
    private val communication: RemoteCommunicationRepository,
    private val notifications: NotificationHelper
) {
    private data class Transfer(
        val id: String,
        val remoteIp: String,
        val peer: PeerConnection,
        var channel: DataChannel? = null,
        var file: File? = null,
        var output: BufferedOutputStream? = null,
        var expected: Long = 0,
        var received: Long = 0,
        var lastNotifiedBytes: Long = 0,
        var name: String = "file",
        var mimeType: String = "application/octet-stream"
    )

    private val transfers = ConcurrentHashMap<String, Transfer>()
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteDescriptionReady = ConcurrentHashMap.newKeySet<String>()
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()
    private val factory: PeerConnectionFactory by lazy { createFactory() }

    fun onSignal(sessionId: String, remoteIp: String, payload: String) {
        try {
            val signal = JSONObject(payload)
            when (signal.optString("kind")) {
                "discover" -> {
                    val name = signal.optString("name", "file")
                    val size = signal.optLong("size", -1L)
                    if (size < 0L || sessionId.isBlank()) return
                    notifications.showWebRtcIncomingNotification(sessionId, remoteIp, name, size)
                }
                "offer" -> acceptOffer(sessionId, remoteIp, signal)
                "candidate" -> {
                    val active = transfers[sessionId]
                    if (active == null || active.remoteIp == remoteIp) addCandidate(sessionId, signal.optJSONObject("candidate"))
                }
                "cancel" -> {
                    if (transfers[sessionId]?.remoteIp == remoteIp) closeTransfer(sessionId, "Canceled by sender")
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Invalid WebRTC signal for $sessionId", error)
        }
    }

    fun accept(sessionId: String, remoteIp: String) {
        if (transfers.containsKey(sessionId)) return
        try {
            val config = PeerConnection.RTCConfiguration(
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            )
            val observer = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    if (state == PeerConnection.IceConnectionState.FAILED) {
                        Log.w(TAG, "WebRTC connection $sessionId failed")
                        fail(sessionId, "Direct connection failed")
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        sendSignal(sessionId, remoteIp, JSONObject().apply {
                            put("kind", "candidate")
                            put("candidate", JSONObject().apply {
                                put("candidate", candidate.sdp)
                                put("sdpMid", candidate.sdpMid)
                                put("sdpMLineIndex", candidate.sdpMLineIndex)
                            })
                        })
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) {
                    channel ?: return
                    val transfer = transfers[sessionId] ?: return
                    transfer.channel = channel
                    channel.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(previousAmount: Long) = Unit
                        override fun onStateChange() = Unit
                        override fun onMessage(buffer: DataChannel.Buffer) {
                            val bytes = ByteArray(buffer.data.remaining())
                            buffer.data.get(bytes)
                            writer.execute { handleData(transfer, bytes, buffer.binary) }
                        }
                    })
                }
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
            }
            val peer = factory.createPeerConnection(config, observer)
                ?: throw IllegalStateException("Could not create native WebRTC peer")
            transfers[sessionId] = Transfer(sessionId, remoteIp, peer)
            sendSignal(sessionId, remoteIp, JSONObject().put("kind", "accept"))
            notifications.updateWebRtcNotification(sessionId, "Connecting directly…", 0, 0)
        } catch (error: Exception) {
            Log.e(TAG, "Could not accept WebRTC request", error)
            notifications.updateWebRtcNotification(sessionId, "Could not start direct transfer", 0, 0)
        }
    }

    fun reject(sessionId: String, remoteIp: String) {
        sendSignal(sessionId, remoteIp, JSONObject().put("kind", "reject"))
        pendingCandidates.remove(sessionId)
        notifications.cancelWebRtcNotification(sessionId)
    }

    private fun acceptOffer(id: String, remoteIp: String, signal: JSONObject) {
        val transfer = transfers[id] ?: return
        if (transfer.remoteIp != remoteIp) return
        val description = signal.optJSONObject("description") ?: return
        val type = SessionDescription.Type.fromCanonicalForm(description.optString("type", "offer"))
        val remote = SessionDescription(type, description.optString("sdp"))
        transfer.peer.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionReady.add(id)
                pendingCandidates.remove(id)?.forEach { transfer.peer.addIceCandidate(it) }
                transfer.peer.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        if (answer == null) return
                        transfer.peer.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                sendSignal(id, remoteIp, JSONObject().apply {
                                    put("kind", "answer")
                                    put("description", JSONObject().put("type", answer.type.canonicalForm()).put("sdp", answer.description))
                                })
                            }
                            override fun onSetFailure(error: String?) { fail(id, error ?: "Could not set local description") }
                            override fun onCreateSuccess(description: SessionDescription?) = Unit
                            override fun onCreateFailure(error: String?) = Unit
                        }, answer)
                    }
                    override fun onCreateFailure(error: String?) { fail(id, error ?: "Could not create answer") }
                    override fun onSetSuccess() = Unit
                    override fun onSetFailure(error: String?) = Unit
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String?) { fail(id, error ?: "Could not accept offer") }
            override fun onCreateSuccess(description: SessionDescription?) = Unit
            override fun onCreateFailure(error: String?) = Unit
        }, remote)
    }

    private fun addCandidate(id: String, json: JSONObject?) {
        json ?: return
        val candidate = IceCandidate(
            json.optString("sdpMid"),
            json.optInt("sdpMLineIndex"),
            json.optString("candidate")
        )
        val transfer = transfers[id]
        if (transfer == null || !remoteDescriptionReady.contains(id)) {
            val queue = pendingCandidates.computeIfAbsent(id) { java.util.Collections.synchronizedList(mutableListOf()) }
            synchronized(queue) {
                if (queue.size < 256) queue.add(candidate)
            }
        } else {
            transfer.peer.addIceCandidate(candidate)
        }
    }

    private fun handleData(transfer: Transfer, bytes: ByteArray, binary: Boolean) {
        try {
            if (!binary) {
                val message = JSONObject(String(bytes, Charsets.UTF_8))
                when (message.optString("type")) {
                    "start" -> {
                        transfer.name = safeName(message.optString("name", "file"))
                        transfer.expected = message.optLong("size", -1L)
                        transfer.mimeType = message.optString("mimeType", "application/octet-stream")
                        if (transfer.expected < 0L) throw IllegalArgumentException("Invalid file size")
                        val publicDownloads = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "LocalShare"
                        )
                        val canWritePublicDownloads = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        val directory = if (canWritePublicDownloads) publicDownloads else
                            (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "Downloads"))
                        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Cannot create download folder")
                        val available = StatFs(directory.absolutePath).availableBytes
                        if (transfer.expected > available) throw IllegalStateException("Not enough free storage space")
                        var destination = File(directory, transfer.name)
                        var suffix = 1
                        val base = destination.nameWithoutExtension
                        val extension = destination.extension
                        while (destination.exists()) {
                            val candidateName = if (extension.isBlank()) "$base ($suffix)" else "$base ($suffix).$extension"
                            destination = File(directory, candidateName)
                            suffix++
                        }
                        transfer.file = destination
                        transfer.output = BufferedOutputStream(FileOutputStream(transfer.file!!, false), 256 * 1024)
                        transfer.received = 0L
                        notifications.updateWebRtcNotification(transfer.id, "Receiving ${transfer.name}", 0, transfer.expected)
                    }
                    "finish" -> {
                        transfer.output?.flush()
                        transfer.output?.close()
                        transfer.output = null
                        if (transfer.expected != transfer.received) {
                            fail(transfer.id, "File size mismatch: ${transfer.received}/${transfer.expected}")
                            return
                        }
                        transfer.channel?.send(DataChannel.Buffer(
                            ByteBuffer.wrap(JSONObject().put("type", "ack").toString().toByteArray(Charsets.UTF_8)), false
                        ))
                        notifications.showWebRtcCompletedNotification(transfer.id, transfer.name, transfer.file)
                        closeTransfer(transfer.id, null)
                    }
                    "cancel" -> closeTransfer(transfer.id, "Canceled by sender")
                }
                return
            }
            val output = transfer.output ?: throw IllegalStateException("File data arrived before start message")
            output.write(bytes)
            transfer.received += bytes.size.toLong()
            if (transfer.received > transfer.expected) throw IllegalStateException("Received more data than announced")
            if (transfer.received - transfer.lastNotifiedBytes >= 4L * 1024L * 1024L) {
                transfer.lastNotifiedBytes = transfer.received
                notifications.updateWebRtcNotification(
                    transfer.id, "Receiving ${transfer.name}",
                    transfer.received, transfer.expected
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "WebRTC file write failed", error)
            fail(transfer.id, error.message ?: "File write failed")
        }
    }

    private fun sendSignal(id: String, remoteIp: String, signal: JSONObject) {
        communication.sendWebRtcSignal(id, remoteIp, signal.toString())
    }

    private fun fail(id: String, reason: String) {
        notifications.updateWebRtcNotification(id, "Transfer failed: $reason", 0, 0)
        closeTransfer(id, reason)
    }

    private fun closeTransfer(id: String, reason: String?) {
        val transfer = transfers.remove(id)
        try { transfer?.output?.close() } catch (_: Exception) { }
        try { transfer?.channel?.dispose() } catch (_: Exception) { }
        try { transfer?.peer?.close() } catch (_: Exception) { }
        try { transfer?.peer?.dispose() } catch (_: Exception) { }
        if (reason != null) {
            try { transfer?.file?.delete() } catch (_: Exception) { }
        }
        pendingCandidates.remove(id)
        if (reason != null) notifications.updateWebRtcNotification(id, reason, 0, 0)
    }

    fun dispose() {
        transfers.keys.toList().forEach { closeTransfer(it, null) }
        writer.shutdown()
    }

    private fun createFactory(): PeerConnectionFactory {
        synchronized(DirectWebRtcReceiver::class.java) {
            if (!initialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
                )
                initialized = true
            }
        }
        return PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun safeName(value: String): String {
        val cleaned = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]"), "_")
            .trim()
        return cleaned.take(180).ifBlank { "received-file" }
    }

    companion object {
        private const val TAG = "DirectWebRtcReceiver"
        @Volatile private var initialized = false
    }
}
