package thirdperson

import net.multigesture.kanama.api.AudioStream
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3

object DemoScenes {
    const val BULLET = "res://player/bullet.tscn"
    const val COIN = "res://player/coin/coin.tscn"
    const val DESTROYED_BOX = "res://box/destroyed_box.tscn"
    const val EXPLOSION = "res://player/explosion_visuals/explosion_scene.tscn"
    const val GRENADE = "res://player/grenade.tscn"
    const val SMOKE_PUFF = "res://enemies/smoke_puff/smoke_puff.tscn"
    const val BEE_BOT = "res://enemies/bee_bot.tscn"
    const val BEETLE_BOT = "res://enemies/beetle_bot.tscn"

    private val warmupAudioPaths = listOf(
        "res://player/sounds/lasershot-102078.wav",
        "res://player/sounds/musket-explosion-6383.wav",
        "res://player/sounds/03_step_grass_03.wav",
        "res://player/sounds/45_landing_01.wav",
        "res://player/coin/audio/completetask_0.mp3",
        "res://box/sounds/crate-break-1-93926.wav",
        "res://enemies/smoke_puff/Sounds/poof_1.wav",
        "res://enemies/smoke_puff/Sounds/poof_2.wav",
        "res://enemies/sounds/robot_defeat.wav",
        "res://enemies/sounds/mechanical_1.wav",
        "res://enemies/sounds/mechanical_2.wav",
        "res://level/music/mountain.mp3",
    )
    private val warmupPaths = listOf(
        BULLET,
        COIN,
        DESTROYED_BOX,
        EXPLOSION,
        GRENADE,
        SMOKE_PUFF,
        BEE_BOT,
        BEETLE_BOT,
    )
    private val warmupInstancePaths = listOf(
        BULLET,
        COIN,
        DESTROYED_BOX,
        EXPLOSION,
        GRENADE,
        SMOKE_PUFF,
        BEE_BOT,
        BEETLE_BOT,
    )
    private val sceneCache = mutableMapOf<String, PackedScene>()
    private val audioCache = mutableMapOf<String, AudioStream>()
    // Android hitches if the first enemy/player bullet is instantiated during combat.
    // Keep a small pool ready so shooting only resets an existing scene.
    private val bulletPool = ArrayDeque<Node>()
    private val pooledBulletHandles = mutableSetOf<Long>()

    fun warmUp(owner: Node? = null) {
        warmupPaths.forEach { path ->
            scene(path)
        }
        warmupAudioPaths.forEach { path ->
            audio(path)
        }
        owner?.let { warmUpInstances(it) }
        owner?.let { warmUpBulletPool(it) }
    }

    fun releaseWarmUp() {
        bulletPool.forEach { node ->
            node.queueFree()
        }
        bulletPool.clear()
        pooledBulletHandles.clear()
        audioCache.clear()
        sceneCache.clear()
    }

    fun instantiate(path: String): Node? {
        return scene(path)?.instantiate()
    }

    fun launchBullet(parent: Node?, shooter: Node, origin: Vector3, velocity: Vector3, distanceLimit: Double): Node? {
        val pooled = takePooledBullet()
        if (pooled != null) {
            val bullet = pooled.kotlinScriptInstance<Bullet>()
                ?: error("Pooled bullet scene is missing Bullet script instance")
            bullet.launch(shooter, origin, velocity, distanceLimit)
            return pooled
        }

        val bulletNode = instantiate(BULLET) ?: return null
        val bullet = bulletNode.kotlinScriptInstance<Bullet>()
            ?: error("Bullet scene is missing Bullet script instance")
        bullet.shooter = shooter
        bullet.velocity = velocity
        bullet.distanceLimit = distanceLimit
        parent?.addChild(bulletNode)
        Node3D(bulletNode.handle).globalPosition = origin
        return bulletNode
    }

    fun recycleBullet(node: Node): Boolean {
        val handle = node.handle.address()
        if (!pooledBulletHandles.contains(handle)) return false

        val spatial = Node3D(node.handle)
        spatial.globalPosition = WARMUP_POSITION
        spatial.setVisible(false)
        node.setProcess(false)
        if (!bulletPool.any { it.handle.address() == handle }) {
            bulletPool.addLast(node)
        }
        return true
    }

    private fun scene(path: String): PackedScene? {
        sceneCache[path]?.let { return it }
        val scene = ResourceLoader.loadPackedScene(path) ?: return null
        sceneCache[path] = scene
        return scene
    }

    private fun audio(path: String): AudioStream? {
        audioCache[path]?.let { return it }
        val stream = ResourceLoader.loadAudioStream(path) ?: return null
        audioCache[path] = stream
        return stream
    }

    private fun warmUpInstances(owner: Node) {
        warmupInstancePaths.forEach { path ->
            val node = instantiate(path) ?: return@forEach
            val spatial = Node3D(node.handle)
            spatial.setVisible(false)
            owner.addChild(node)
            spatial.globalPosition = WARMUP_POSITION
            node.queueFree()
        }
    }

    private fun warmUpBulletPool(owner: Node) {
        if (pooledBulletHandles.isNotEmpty()) return

        repeat(BULLET_POOL_SIZE) {
            val node = instantiate(BULLET) ?: return@repeat
            val spatial = Node3D(node.handle)
            spatial.setVisible(false)
            owner.addChild(node)
            spatial.globalPosition = WARMUP_POSITION
            node.setProcess(false)
            pooledBulletHandles.add(node.handle.address())
            bulletPool.addLast(node)
        }
    }

    private fun takePooledBullet(): Node? =
        if (bulletPool.isEmpty()) null else bulletPool.removeFirst()

    private val WARMUP_POSITION = Vector3(0.0, -10_000.0, 0.0)
    private const val BULLET_POOL_SIZE = 8
}
