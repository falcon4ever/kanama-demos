import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.ManualGodotLifetimeApi
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.binding.runtime.ObjectCalls
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node2D")
@OptIn(ManualGodotLifetimeApi::class)
class BunnymarkV3Kanama(godotObject: MemorySegment) : KanamaScript<Node2D>(godotObject, ::Node2D) {
    private lateinit var screenSize: Vector2
    private lateinit var label: Label
    private lateinit var bunnies: Node2D
    private var bunnyTexture: Texture2D? = null
    private var bunnyScript: Resource? = null

    @OnReady
    fun ready() {
        GD.randomize()
        screenSize = self.getViewportRect().size
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
        bunnyScript = ResourceLoader.load("res://kotlin-src/BunnyKanama.kt")
        bunnies = Node2D(ObjectCalls.constructObject("Node2D"))
        self.addChild(bunnies)
        label = Label(ObjectCalls.constructObject("Label"))
        label.position = Vector2(0.0, 20.0)
        self.addChild(label)
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyScript?.close()
        bunnyTexture = null
        bunnyScript = null
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size
        label.text = "Bunnies: ${bunnies.getChildCount()}"
    }

    @RegisterFunction("add_bunny")
    fun addBunny() {
        val bunny = Sprite2D(ObjectCalls.constructObject("Sprite2D"))
        bunny.setScript(bunnyScript)
        bunny.setTexture(bunnyTexture)
        bunnies.addChild(bunny)
        bunny.set("speed", Vector2((GD.randi() % 200L + 50L).toDouble(), (GD.randi() % 200L + 50L).toDouble()))
        bunny.position = Vector2(screenSize.x / 2.0, screenSize.y / 2.0)
    }

    @RegisterFunction("remove_bunny")
    fun removeBunny() {
        val childCount = bunnies.getChildCount().toInt()
        if (childCount == 0) return
        val bunny = bunnies.getChild(childCount - 1) ?: return
        bunnies.removeChild(bunny)
        bunny.queueFree()
    }

    @RegisterFunction("finish")
    fun finish() {
        self.emitSignal("benchmark_finished", bunnies.getChildCount())
    }
}
