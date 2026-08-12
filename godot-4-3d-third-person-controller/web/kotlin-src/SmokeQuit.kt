package thirdperson

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.call
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3

/**
 * Web variant of the smoke root. The desktop SmokeQuit drives an env-gated scripted self-test
 * (eight routines, including two `damage(Vector3, Vector3)` calls); on Web the browser driver
 * (kanama scripts/web/drivers/demos/thirdperson.mjs) choreographs the run from outside through
 * the registered methods below. Method ids follow declaration order and the driver pins
 * method#1/method#2, so new methods must APPEND.
 *
 * - Method#1 `smoke_resume` presses through the pause page.
 * - Method#2 `smoke_teardown` releases the scene caches and frees the root so live handles
 *   drain to zero.
 * - Method#3 `smoke_combat` is the Web port of the desktop smoke's damage routines (task 81
 *   fix #3): `damage(Vector3, Vector3)` on a live bee and beetle, dispatched through
 *   Object.call exactly like Bullet/Grenade/MeleeAttackArea combat -- the registered-method
 *   shape that is the FPS `damage` bug's sibling. The driver watches liveScriptsByClass for
 *   both death frees and the exercised-member census gates BeeBot.damage/BeetleBot.damage.
 */
@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @RegisterFunction("smoke_resume")
  fun smokeResume() {
    // The DemoPage boots the tree paused; resume through its own flow so page state stays
    // consistent (falls back to a raw unpause if the page is missing).
    val page =
      self.getParent()?.let { Node(it.handle) }?.getNodeOrNull("DemoPage")
        ?.kotlinScriptInstance<DemoPage>()
    if (page != null) {
      page.resumeFromSmoke()
    } else {
      self.getTree().setPaused(false)
    }
  }

  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    DemoScenes.releaseWarmUp()
    val root = self.getParent() ?: error("SmokeQuit has no parent to tear down")
    Node(root.handle).queueFree()
  }

  @RegisterFunction("smoke_combat")
  fun smokeCombat() {
    // The scene's own bots, the two nearest to the player's start so killing them also
    // removes the only AI that could reach the driver's stance mid-run (paths from
    // main.tscn). The desktop smoke instantiates fresh bots instead; damaging live scene
    // bots keeps the Web observable simple -- liveScriptsByClass drops when the death
    // coroutine's queue_free lands, so the driver can assert the damage EFFECT, not just
    // the dispatch.
    val root = self.getParent() ?: error("SmokeQuit has no parent to run smoke_combat on")
    val rootNode = Node(root.handle)
    // Warm the death-puff scene into the cache under THIS long-lived script's ownership
    // first: a dying bot would otherwise be the cache entry's owner and its death sweep
    // would leave a dead resource handle for releaseWarmUp to close (see DemoScenes.warmUp).
    DemoScenes.warmUp(DemoScenes.SMOKE_PUFF)
    damageFoe(rootNode, "Foes/FlyingEnemy2") { it.kotlinScriptInstance<BeeBot>()?.coinsCount = 0 }
    damageFoe(rootNode, "Foes/GroundEnemy") { it.kotlinScriptInstance<BeetleBot>()?.coinsCount = 0 }
  }

  private fun damageFoe(root: Node, path: String, zeroCoins: (Node) -> Unit) {
    val foe = root.getNodeOrNull(path) ?: error("smoke_combat: $path is missing from the scene")
    val foeNode = Node(foe.handle)
    // Dying bots burst coins, and coins chase the player once close (Coin.kt's target
    // vacuum). The coin HUD slide is a KNOWN Web gap (task 81 confirmed break #3: the
    // CoinsContainer tween passes a NUMBER to web Tween.tween_property, which only
    // supports Vector2/Color/Vector3 and faults) -- zero the burst so the combat port
    // cannot trip the unrelated, already-tracked coin-HUD defect.
    zeroCoins(foeNode)
    // Same arguments as the desktop smoke (kotlin-src/SmokeQuit.kt): a zero impact point
    // and a gentle +Z force -- the damage itself, not the ragdoll, is the assertion.
    GodotObject(foe.handle).call("damage", Vector3.ZERO, Vector3(0.0, 0.0, 1.0))
  }
}
