package charactercontroller

import net.multigesture.kanama.api.Node

/** Web adaptation: the Events autoload resolves by absolute path (FPS Audio precedent). */
fun Node.eventsNode(): Node =
  getNodeOrNull("/root/Events")?.let { Node(it.handle) } ?: error("Events autoload is missing")
