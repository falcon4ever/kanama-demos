package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.generated.EventsNames
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CanvasLayer")
class FlagReachedScreen(godotObject: GodotHandle) :
  KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()

  private lateinit var animationPlayer: AnimationPlayer

  @OnReady
  fun ready() {
    animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
    val events = self.eventsNode()
    events.signal(EventsNames.Signals.flagReached).connect(self, argumentCount = 0) {
      kanamaScope.launch {
        self.getTree().delaySeconds(2.0)
        animationPlayer.play("fade_in")
        animationPlayer
          .signal(AnimationMixer.Signals.animationFinished)
          .await(self, argumentCount = 1)
        // Restart the level instead of quitting the app: app-quit win behavior is wrong
        // for a touch/GUI build and for a browser page alike.
        self.getTree().reloadCurrentScene()
      }
    }
  }
}
