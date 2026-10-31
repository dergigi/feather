@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import android.app.ActivityOptions
import android.os.Build
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The service owns playback, so closing a screen does not stop the audio. */
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val speedCommand = SessionCommand("org.dergigi.feather.audio.CYCLE_SPEED", Bundle.EMPTY)

    private val backCommand = SessionCommand("org.dergigi.feather.audio.SEEK_BACK", Bundle.EMPTY)
    private val forwardCommand = SessionCommand("org.dergigi.feather.audio.SEEK_FORWARD", Bundle.EMPTY)
    private val heartCommand = SessionCommand("org.dergigi.feather.audio.HEART", Bundle.EMPTY)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as FeatherApplication

    private fun controls(player: Player): List<CommandButton> {
        val speedIcon = audioSpeedIcon(player.playbackParameters.speed)
        val note = app.audio.sourceNote
        val state = app.repository.state.value
        val reacted = note?.id?.let { id -> state.sentReactions[id].orEmpty().any { it.content == "🧡" } } == true
        return buildList {
            add(CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10).setSessionCommand(backCommand)
                .setDisplayName(getString(R.string.ui_rewind)).setSlots(CommandButton.SLOT_BACK)
                .setEnabled(player.isCommandAvailable(Player.COMMAND_SEEK_BACK)).build())
            add(CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30).setSessionCommand(forwardCommand)
                .setDisplayName(getString(R.string.ui_forward)).setSlots(CommandButton.SLOT_FORWARD)
                .setEnabled(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)).build())
            add(CommandButton.Builder(CommandButton.ICON_UNDEFINED).setCustomIconResId(speedIcon)
                .setSessionCommand(speedCommand).setDisplayName(getString(R.string.ui_change_speed, audioSpeedLabel(player.playbackParameters.speed)))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW).build())
            if (note != null) add(CommandButton.Builder(if (reacted) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setSessionCommand(heartCommand).setDisplayName(if (reacted) getString(R.string.ui_heart_sent) else getString(R.string.ui_heart_send))
                .setEnabled(state.publicKey != null && !reacted)
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW).build())
        }
    }

    private fun noteIntent(heartToken: String? = app.audio.pendingHeartToken(app.repository.state.value.publicKey)): PendingIntent {
        val note = app.audio.sourceNote
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (note != null) intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse("nostr:${note.nostrPointer()}"))
        heartToken?.let { intent.putExtra("audio-heart", it) }
        val options = if (Build.VERSION.SDK_INT >= 35) ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle() else null
        return PendingIntent.getActivity(this, if (heartToken == null) 0 else 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options)
    }

    private fun refreshControls() {
        val current = session ?: return
        val buttons = controls(current.player)
        current.setCustomLayout(buttons) // Older controllers use the compatibility layout.
        current.setMediaButtonPreferences(buttons)
        current.setSessionActivity(noteIntent())
    }
    private val media get() = (application as FeatherApplication).media

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.notification_feather)
        })
        instance = this
        // The UI only connects after the media cache has initialized off-thread.
        val resources = media.resources.value ?: return
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resources.videoSource))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10000, 30000, 1000, 2000)
                .setTargetBufferBytes(4 * 1024 * 1024).build())
            .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(30000)
            .build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        player.setHandleAudioBecomingNoisy(true)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION)) refreshControls()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) VideoPlaybackService.stopPlayback()
                media.audioActive = playWhenReady
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) player.pause()
            }
            override fun onPlayerError(error: PlaybackException) {
                player.pause()
                AppMessages.show("Audio: ${walletErrorText(error.message ?: error.errorCodeName)}")
            }
        })
        session = MediaSession.Builder(this, player)
            .setMediaButtonPreferences(controls(player))
            .setSessionActivity(noteIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    if (controller.packageName == packageName || controller.isTrusted)
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                            .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(speedCommand).add(backCommand).add(forwardCommand).add(heartCommand).build())
                            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_NEXT)
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build())
                            .build()
                    else MediaSession.ConnectionResult.reject()

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    val player = session.player
                    when (customCommand.customAction) {
                        backCommand.customAction -> if (player.isCommandAvailable(Player.COMMAND_SEEK_BACK)) player.seekBack()
                        forwardCommand.customAction -> if (player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)) player.seekForward()
                        speedCommand.customAction -> {
                            val index = audioSpeeds.indexOf(player.playbackParameters.speed)
                            player.setPlaybackSpeed(audioSpeeds[(index + 1) % audioSpeeds.size])
                        }
                        heartCommand.customAction -> {
                            val owner = app.repository.state.value.publicKey
                                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                            val token = app.audio.requestHeart(owner)
                                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                            val intent = noteIntent(token)
                            // A tap on the player also completes the explicitly requested heart
                            // if Android requires unlocking before opening the signer flow.
                            session.setSessionActivity(intent)
                            try {
                                val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().apply {
                                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                }.toBundle() else null
                                intent.send(this@AudioPlaybackService, 0, null, null, null, null, options)
                            } catch (_: PendingIntent.CanceledException) {
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                            }
                        }
                        else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                    if (controller.packageName == packageName) mediaItems.take(1).filter {
                        MediaContent.mediaUrl(it.localConfiguration?.uri?.toString()) != null
                    } else emptyList())
            }).build()
        serviceScope.launch {
            app.repository.state.map { it.publicKey to it.sentReactions[app.audio.sourceNote?.id].orEmpty() }
                .distinctUntilChanged().collect { refreshControls() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun releasePlayback() {
        session?.run { player.release(); release() }
        session = null
        media.audioActive = false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releasePlayback()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: AudioPlaybackService? = null
        internal fun sourceChanged() { instance?.refreshControls() }
        /** Main thread, before the shared cache is closed/replaced on IO. */
        internal fun beforeCacheChange() {
            instance?.let { it.releasePlayback(); it.stopSelf() }
        }
    }
}
