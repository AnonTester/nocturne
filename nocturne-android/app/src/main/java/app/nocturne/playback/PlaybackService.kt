package app.nocturne.playback

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.nocturne.NocturneApp

/** Foreground media session: notification, lock-screen and headset controls. The engine owns the player. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = (application as NocturneApp).engine
        // ExoPlayer only holds one item; route prev/next to the engine's queue logic.
        val wrapped = object : ForwardingPlayer(engine.player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
            override fun hasNextMediaItem() = true
            override fun hasPreviousMediaItem() = true
            override fun seekToNext() = engine.next()
            override fun seekToNextMediaItem() = engine.next()
            override fun seekToPrevious() = engine.previous()
            override fun seekToPreviousMediaItem() = engine.previous()
            override fun play() = engine.play()
            override fun pause() = engine.pause()
        }
        session = MediaSession.Builder(this, wrapped).build().also {
            // Registers the session so Media3 posts (and updates) the foreground media notification.
            addSession(it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Launcher app: keep playing when the task is swiped away.
    }

    override fun onDestroy() {
        session?.run { release() }
        session = null
        super.onDestroy()
    }
}
