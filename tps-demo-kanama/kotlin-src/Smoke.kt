package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.LineEdit
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.SpinBox
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3

/**
 * Every smoke hook of this demo, as an autoload (Kanama task 131 item 17): the gameplay classes
 * (Main, Menu, Level, Part, PartDisappear) keep no smoke code and no env checks. The autoload
 * outlives the menu/level scene swaps, watches the tree for those nodes through `node_added`,
 * and drives them from outside, each switch keeping the `KANAMA_TPS_SMOKE_*` name and the log
 * line the Gradle smokes assert on.
 *
 * Active only when a `KANAMA_TPS_SMOKE_*` switch is set, so a player's run, a headless dedicated
 * server included, connects nothing. The dedicated-server behaviour itself (headless max fps, host
 * and start at once) is upstream gameplay and lives in Main and Menu. The Web build overrides this file (web/kotlin-src/Smoke.kt) with
 * the browser harness entry points, which the driver finds by name in the export manifest.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {

    private var levelReadyCount = 0

    @OnReady
    fun ready() {
        // Inert unless a smoke switch is set: a plain run, headless dedicated server included,
        // connects nothing.
        if (ENV_SWITCHES.none { System.getenv(it) != null }) return
        val tree = requireNotNull(self.getTree())
        tree.nodeAdded.connect { node ->
            node.kotlinScriptInstance<Menu>()?.let { menu -> onMenuAdded(menu) }
            node.kotlinScriptInstance<Level>()?.let { level -> launch { onLevel(level) } }
            node.kotlinScriptInstance<Part>()?.let { part -> onPart(part) }
        }
    }

    // Menu: a joined or hosted game without a click. node_added fires before the menu's ready(),
    // so what ready() reads (the port, the auto-host switches) is set here, synchronously. The
    // headless run hosts at once by itself (Menu); a joining client must turn that off.
    private fun onMenuAdded(menu: Menu) {
        System.getenv("KANAMA_TPS_SMOKE_PORT")?.toDoubleOrNull()?.let {
            menu.self.requireAs("UI/Online/Port", ::SpinBox).value = it
        }
        val joinAddress = System.getenv("KANAMA_TPS_SMOKE_JOIN_ADDRESS")
        if (!joinAddress.isNullOrBlank()) {
            menu.autoStartHostedGame = false
            val address = menu.self.requireAs("UI/Online/Address", ::LineEdit)
            launch {
                MainThread.awaitNextFrame()
                address.text = joinAddress
                menu.onConnectPressed()
            }
            return
        }
        if (System.getenv("KANAMA_TPS_SMOKE_AUTOSTART") == "1") {
            menu.autoStartHostedGame = true
        }
        menu.autoStartAfterPeer = System.getenv("KANAMA_TPS_SMOKE_WAIT_FOR_PEER") == "1"
    }

    // Level: the reload count, the scripted robot kills and the multiplayer exit checks.
    private suspend fun onLevel(level: Level) {
        MainThread.awaitNextFrame() // the level's own ready (robots, players) has run by now
        val reloaded = observeLevelReady()
        val server = level.self.isMultiplayerServer()
        if (server && !reloaded) {
            killRobotsIfRequested(level)
        } else if (!server) {
            quitWhenHostLeavesIfRequested(level)
        }
        if (reloaded) {
            wait(5.0)
            if (alive(level.self)) {
                GD.print("TPS smoke second level ready; quitting")
                requireNotNull(self.getTree()).quit()
            }
        }
    }

    /**
     * Reload-smoke bookkeeping: the desktop smoke enters the level twice in one run and quits on
     * the second ready. The count lives in a marker file when the harness names one, so a reload
     * that loses the in-process counter is still seen. The marker is a Godot `ConfigFile` -- the
     * file API both backends share -- rather than `java.io.File`.
     */
    private fun observeLevelReady(): Boolean {
        if (System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_SECOND_LEVEL_READY") != "1") return false
        val markerPath = System.getenv("KANAMA_TPS_SMOKE_RELOAD_MARKER")
        levelReadyCount += 1
        val readyCount = if (markerPath.isNullOrBlank()) levelReadyCount else bumpReloadMarker(markerPath)
        return readyCount >= 2
    }

    private fun bumpReloadMarker(markerPath: String): Int {
        // close what you create (Kanama task 61): the marker ConfigFile is ours for this call only.
        val marker = TpsFactory.configFile()
        try {
            marker.load(markerPath)
            val previous = if (marker.hasSectionKey(MARKER_SECTION, MARKER_KEY)) {
                (marker.getValue(MARKER_SECTION, MARKER_KEY) as? Number)?.toInt() ?: 0
            } else {
                0
            }
            val nextCount = previous + 1
            marker.setValue(MARKER_SECTION, MARKER_KEY, nextCount.toLong())
            marker.save(markerPath)
            return nextCount
        } finally {
            marker.close()
        }
    }

    /**
     * Multiplayer-smoke exit check: the host quits normally (`SceneTree.quit`) with the client
     * still connected, so the harness can assert a clean process exit.
     */
    private suspend fun quitAfterRobotDeathIfRequested(level: Level, lastKill: Boolean) {
        if (!lastKill || System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_ROBOT_DEATH") != "1") return
        wait(System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_ROBOT_DEATH_DELAY")?.toDoubleOrNull() ?: 2.0)
        GD.print("TPS smoke host quitting with peers connected=${level.self.multiplayerPeers().size}")
        requireNotNull(self.getTree()).quit()
    }

    /** Multiplayer-smoke exit check, client side: quit normally once the host has gone. */
    private fun quitWhenHostLeavesIfRequested(level: Level) {
        if (System.getenv("KANAMA_TPS_SMOKE_QUIT_WHEN_HOST_LEAVES") != "1") return
        level.self.withMultiplayer { api ->
            api.serverDisconnected.connect {
                launch {
                    wait(1.0)
                    GD.print("TPS smoke client quitting after the host left")
                    requireNotNull(self.getTree()).quit()
                }
            }
        }
    }

    private suspend fun killRobotsIfRequested(level: Level) {
        if (System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOT") != "1") return
        val robotCount = System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOTS")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val delayBetweenKills = System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOT_DELAY")?.toDoubleOrNull() ?: 3.0
        val useRealBullets = System.getenv("KANAMA_TPS_SMOKE_REAL_BULLETS") == "1"
        val spawnedNodes = level.self.requireAs("SpawnedNodes", ::Node3D)
        repeat(robotCount) { index ->
            if (index > 0) {
                wait(delayBetweenKills)
            }
            if (!alive(level.self)) return
            MainThread.awaitNextFrame()
            if (!alive(level.self)) return
            val robot = spawnedNodes.getChildren()
                .map { Node3D(it.handle) }
                .firstOrNull {
                    !it.isQueuedForDeletion() &&
                        it.isInsideTree() &&
                        it.kotlinScriptInstance<RedRobot>()?.dead != true
                }
            if (robot == null) {
                GD.print("TPS smoke robot death skipped: no live robot found for kill ${index + 1}")
            } else if (useRealBullets) {
                launch {
                    repeat(5) { shot ->
                        if (!alive(robot)) return@launch
                        MainThread.awaitNextFrame()
                        if (alive(robot)) spawnBullet(spawnedNodes, robot)
                        if (shot < 4) {
                            wait(0.35)
                        }
                    }
                    wait(2.0)
                    GD.print("TPS smoke robot death complete ${index + 1}/$robotCount")
                    quitAfterRobotDeathIfRequested(level, index + 1 == robotCount)
                }
            } else {
                val script = robot.kotlinScriptInstance<RedRobot>()
                if (script == null) {
                    GD.print("TPS smoke robot death skipped: robot script not ready for kill ${index + 1}")
                } else {
                    repeat(5) {
                        script.hit()
                    }
                    GD.print("TPS smoke robot death complete ${index + 1}/$robotCount")
                    launch { quitAfterRobotDeathIfRequested(level, index + 1 == robotCount) }
                }
            }
            if (index == 0 && System.getenv("KANAMA_TPS_SMOKE_RETURN_TO_MENU_AFTER_FIRST_KILL") == "1") {
                wait(1.0)
                if (alive(level.self)) {
                    GD.print("TPS smoke returning to menu after first kill")
                    level.self.emitSignal("quit")
                }
                return
            }
        }
    }

    private fun spawnBullet(spawnedNodes: Node3D, target: Node3D) {
        val bulletNode = TpsScenes.instantiate(TpsScenes.BULLET) ?: return
        val bullet = CharacterBody3D(bulletNode.handle)
        val targetPosition = target.globalTransform.origin + Vector3.UP
        val origin = targetPosition + Vector3(0.0, 0.0, 4.0)
        spawnedNodes.addChild(bulletNode, true)
        bullet.lookAtFromPosition(origin, targetPosition)
    }

    // Part: shorten the fade-out, and report when a part has been destroyed (hidden), which on
    // the multiplayer client only happens when the destroy RPC arrived.
    private fun onPart(part: Part) {
        if (System.getenv("KANAMA_TPS_SMOKE_FAST_PARTS") == "1") {
            part.lifetime = 0.1
            part.lifetimeRandom = 0.0
        }
        if (System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED") != "1") return
        var reported = false
        part.self.visibilityChanged.connect {
            if (reported || part.self.isVisible()) return@connect
            reported = true
            GD.print("TPS smoke part destroyed")
            launch {
                wait(System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED_DELAY")?.toDoubleOrNull() ?: 4.0)
                requireNotNull(self.getTree()).quit()
            }
        }
    }

    private fun alive(node: Node): Boolean = !node.isQueuedForDeletion() && node.isInsideTree()

    private companion object {
        const val MARKER_SECTION = "smoke"
        const val MARKER_KEY = "level_ready_count"
        val ENV_SWITCHES = listOf(
            "KANAMA_TPS_SMOKE_PORT",
            "KANAMA_TPS_SMOKE_JOIN_ADDRESS",
            "KANAMA_TPS_SMOKE_AUTOSTART",
            "KANAMA_TPS_SMOKE_KILL_ROBOT",
            "KANAMA_TPS_SMOKE_QUIT_AFTER_SECOND_LEVEL_READY",
            "KANAMA_TPS_SMOKE_QUIT_WHEN_HOST_LEAVES",
            "KANAMA_TPS_SMOKE_FAST_PARTS",
            "KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED",
        )
    }
}
