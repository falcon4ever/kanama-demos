package squash

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Label")
class ScoreLabel(godotObject: MemorySegment) : KanamaScript<Label>(godotObject, ::Label) {
    private var score = 0L

    @RegisterFunction("_on_Mob_squashed")
    fun onMobSquashed() {
        score += 1
        self.text = "Score: $score"
    }
}
