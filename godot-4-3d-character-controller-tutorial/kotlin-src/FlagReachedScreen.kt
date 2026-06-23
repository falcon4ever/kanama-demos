package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.generated.EventsNames
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CanvasLayer")
class FlagReachedScreen(godotObject: MemorySegment) : KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer), KanamaCoroutineOwner {
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
                animationPlayer.signal(AnimationMixer.Signals.animationFinished).await(self, argumentCount = 1)
                // Mobile-appropriate win behavior: restart the level instead of quitting the app
                // (desktop unloaded the scene + quit, which is wrong for a touch/GUI build).
                self.getTree().reloadCurrentScene()
            }
        }
    }
}
