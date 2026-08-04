package charactercontroller

import net.multigesture.kanama.api.Node

/** The Events autoload resolves by absolute path — portable on both platforms. */
fun Node.eventsNode(): Node =
  getNodeOrNull("/root/Events")?.let { Node(it.handle) } ?: error("Events autoload is missing")
