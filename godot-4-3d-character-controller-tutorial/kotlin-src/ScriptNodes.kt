package charactercontroller

import net.multigesture.kanama.api.Node

fun Node.eventsNode(): Node =
    getTreeRootNode().getAsOrNull("Events", ::Node)
        ?: error("Events autoload is missing")

private fun Node.getTreeRootNode(): Node =
    Node(getTree().getRoot())
