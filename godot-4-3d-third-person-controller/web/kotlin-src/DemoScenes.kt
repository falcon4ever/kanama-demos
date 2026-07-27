@file:OptIn(net.multigesture.kanama.api.ManualGodotLifetimeApi::class)

package thirdperson

import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.setProcess
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the scene registry. Adaptations: no audio warm-up (the browser decodes lazily and
 * the Web pipeline has no AudioStream cache surface), and handles compare by their Int value.
 */
object DemoScenes {
  const val BULLET = "res://player/bullet.tscn"
  const val COIN = "res://player/coin/coin.tscn"
  const val DESTROYED_BOX = "res://box/destroyed_box.tscn"
  const val EXPLOSION = "res://player/explosion_visuals/explosion_scene.tscn"
  const val GRENADE = "res://player/grenade.tscn"
  const val SMOKE_PUFF = "res://enemies/smoke_puff/smoke_puff.tscn"

  private val sceneCache = mutableMapOf<String, PackedScene>()
  private val bulletPool = ArrayDeque<Node>()
  private val pooledBulletHandles = mutableSetOf<Int>()

  fun releaseWarmUp() {
    bulletPool.forEach { node -> node.queueFree() }
    bulletPool.clear()
    pooledBulletHandles.clear()
    sceneCache.values.forEach { it.close() }
    sceneCache.clear()
  }

  fun instantiate(path: String): Node? = scene(path)?.instantiate()?.let { Node(it.handle) }

  fun launchBullet(
    parent: Node?,
    shooter: Node,
    origin: Vector3,
    velocity: Vector3,
    distanceLimit: Double,
  ): Node? {
    val pooled = takePooledBullet()
    if (pooled != null) {
      val bullet =
        pooled.kotlinScriptInstance<Bullet>()
          ?: error("Pooled bullet scene is missing Bullet script instance")
      bullet.launch(shooter, origin, velocity, distanceLimit)
      return pooled
    }

    val bulletNode = instantiate(BULLET) ?: return null
    val bullet =
      bulletNode.kotlinScriptInstance<Bullet>()
        ?: error("Bullet scene is missing Bullet script instance")
    bullet.shooter = shooter
    bullet.velocity = velocity
    bullet.distanceLimit = distanceLimit
    parent?.addChild(bulletNode)
    Node3D(bulletNode.handle).globalPosition = origin
    return bulletNode
  }

  fun recycleBullet(node: Node): Boolean {
    val handle = node.handle.value
    if (!pooledBulletHandles.contains(handle)) return false

    val spatial = Node3D(node.handle)
    spatial.globalPosition = WARMUP_POSITION
    spatial.visible = false
    node.setProcess(false)
    if (!bulletPool.any { it.handle.value == handle }) {
      bulletPool.addLast(node)
    }
    return true
  }

  /** Pool bullets against an owner so combat only resets existing scenes. */
  fun warmUpBulletPool(owner: Node) {
    if (pooledBulletHandles.isNotEmpty()) return

    repeat(BULLET_POOL_SIZE) {
      val node = instantiate(BULLET) ?: return@repeat
      val spatial = Node3D(node.handle)
      spatial.visible = false
      owner.addChild(node)
      spatial.globalPosition = WARMUP_POSITION
      node.setProcess(false)
      pooledBulletHandles.add(node.handle.value)
      bulletPool.addLast(node)
    }
  }

  private fun scene(path: String): PackedScene? {
    sceneCache[path]?.let { return it }
    val scene = ResourceLoader.loadPackedScene(path) ?: return null
    sceneCache[path] = scene
    return scene
  }

  private fun takePooledBullet(): Node? =
    if (bulletPool.isEmpty()) null else bulletPool.removeFirst()

  private val WARMUP_POSITION = Vector3(0.0, -10_000.0, 0.0)
  private const val BULLET_POOL_SIZE = 4
}
