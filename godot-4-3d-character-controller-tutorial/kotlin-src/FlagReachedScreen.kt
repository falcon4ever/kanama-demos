package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.EventsNames

@ScriptClass(attachTo = "CanvasLayer")
class FlagReachedScreen(godotObject: GodotHandle) :
  KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer) {

  private lateinit var animationPlayer: AnimationPlayer

  @OnReady
  fun ready() {
    animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
    val events = Autoloads.Events.self
    events.signal(EventsNames.Signals.flagReached).connect(self, argumentCount = 0) {
      launch {
        requireNotNull(self.getTree()).delaySeconds(2.0)
        animationPlayer.play("fade_in")
        animationPlayer
          .animationFinished.await()
        // Restart the level instead of quitting the app: app-quit win behavior is wrong
        // for a touch/GUI build and for a browser page alike.
        requireNotNull(self.getTree()).reloadCurrentScene()
      }
    }
  }
}
