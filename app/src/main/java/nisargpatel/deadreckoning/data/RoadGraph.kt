package nisargpatel.deadreckoning.data

import org.osmdroid.util.GeoPoint
import java.util.PriorityQueue

/**
 * Directed road graph built from [RoadSegment]s.
 *
 * Immutable after construction — a new instance is built whenever the
 * segment set changes, and the owning [OfflineRoadNetwork] swaps the
 * reference atomically via @Volatile.
 */
internal class RoadGraph(
    segments: List<RoadSegment>,
    private val restrictions: List<TurnRestriction>
) {
    internal data class Edge(
        val toNode: Long,
        val wayId: Long,
        val distanceMeters: Double,
        val travelSeconds: Double
    )

    /** Node positions, keyed by OSM node ID. */
    val nodes: Map<Long, GeoPoint>

    /** Adjacency list (directed), keyed by source node ID. */
    val adjacency: Map<Long, List<Edge>>

    /**
     * Nodes belonging to each way, for wayId-aware snapping.
     * Maps wayId → list of (nodeId, position) pairs.
     */
    private val wayNodes: Map<Long, List<Pair<Long, GeoPoint>>>

    /**
     * Spatial grid over graph nodes, same 0.01° cell scheme as
     * [OfflineRoadNetwork]'s segment index. Fallback for nearest-node
     * lookups when wayId is 0 (unknown) or not in the graph.
     */
    private val nodeSpatialIndex: Map<String, List<Pair<Long, GeoPoint>>>

    init {
        val nodeMap = HashMap<Long, GeoPoint>()
        val graph = HashMap<Long, MutableList<Edge>>()
        val wayNodeMap = HashMap<Long, MutableList<Pair<Long, GeoPoint>>>()

        segments.forEach { segment ->
            val pairs = segment.points.zip(segment.nodeIds)
            pairs.forEach { (point, id) -> nodeMap[id] = point }
            // Build wayNodes index
            wayNodeMap.getOrPut(segment.wayId) { mutableListOf() }
                .addAll(pairs.map { (point, id) -> id to point })
            // Build adjacency
            segment.nodeIds.zipWithNext().forEachIndexed { i, (from, to) ->
                val dist = segment.points[i].distanceToAsDouble(segment.points[i + 1])
                val speed = (segment.maxSpeedKph ?: defaultSpeed(segment.highway)) / 3.6
                val seconds = dist / speed
                graph.getOrPut(from) { mutableListOf() }
                    .add(Edge(to, segment.wayId, dist, seconds))
                if (!segment.oneWay) {
                    graph.getOrPut(to) { mutableListOf() }
                        .add(Edge(from, segment.wayId, dist, seconds))
                }
            }
        }

        nodes = nodeMap
        adjacency = graph
        wayNodes = wayNodeMap.mapValues { (_, pairs) -> pairs.distinctBy { it.first } }
        nodeSpatialIndex = nodeMap.entries
            .groupBy({ cellId(it.value) }, { it.key to it.value })
    }

    val isEmpty: Boolean get() = nodes.isEmpty()

    // ── wayId-aware nearest-node lookup ──────────────────────────────

    /**
     * Find the nearest graph node to [point] that belongs to [wayId].
     *
     * When [wayId] is 0 (unknown) or absent from the graph, falls back
     * to the spatial-grid nearest-node lookup across all ways.
     *
     * Why wayId matters: near a divided highway, river crossing, or
     * complex intersection, the geometrically-nearest node can belong
     * to a different road than the one the candidate is projected onto.
     * Snapping to the wrong road would start the Dijkstra search from
     * the wrong edge — exactly the failure mode this change exists to fix.
     */
    fun nearestNodeOnWay(point: GeoPoint, wayId: Long): Long? {
        if (wayId != 0L) {
            val candidates = wayNodes[wayId]
            if (candidates != null && candidates.isNotEmpty()) {
                return candidates.minBy { it.second.distanceToAsDouble(point) }.first
            }
        }
        return nearestNodeAnywhere(point)
    }

    /**
     * Spatial-grid nearest-node lookup across all ways.
     * O(local nodes in 9-cell neighbourhood), not O(all nodes).
     */
    private fun nearestNodeAnywhere(point: GeoPoint): Long? {
        val latCell = (point.latitude * 100).toInt()
        val lonCell = (point.longitude * 100).toInt()
        var bestId: Long? = null
        var bestDist = Double.MAX_VALUE
        for (lat in latCell - 1..latCell + 1) {
            for (lon in lonCell - 1..lonCell + 1) {
                nodeSpatialIndex["$lat:$lon"]?.forEach { (id, nodePoint) ->
                    val d = point.distanceToAsDouble(nodePoint)
                    if (d < bestDist) { bestDist = d; bestId = id }
                }
            }
        }
        // Fallback: if grid neighbourhood is empty (very sparse graph)
        if (bestId == null && nodes.isNotEmpty()) {
            nodes.forEach { (id, nodePoint) ->
                val d = point.distanceToAsDouble(nodePoint)
                if (d < bestDist) { bestDist = d; bestId = id }
            }
        }
        return bestId
    }

    // ── Shortest path (distance-weighted, bounded, directed) ─────────

    /**
     * Shortest network distance in metres from [from] to [to], respecting
     * one-way edges and turn restrictions.
     *
     * @param from      projected point on [fromWayId]
     * @param fromWayId way the "from" candidate is projected onto; used
     *                  to snap to the correct road's node, not a parallel one
     * @param to        projected point on [toWayId]
     * @param toWayId   way the "to" candidate is projected onto
     * @param maxMeters search bound; Dijkstra stops expanding when cost exceeds this
     * @return shortest path distance in metres, or null if unreachable within [maxMeters]
     */
    fun shortestPathMeters(
        from: GeoPoint, fromWayId: Long,
        to: GeoPoint, toWayId: Long,
        maxMeters: Double
    ): Double? {
        if (isEmpty) return null
        val startId = nearestNodeOnWay(from, fromWayId) ?: return null
        val endId = nearestNodeOnWay(to, toWayId) ?: return null

        val partialFrom = from.distanceToAsDouble(nodes[startId]!!)
        val partialTo = to.distanceToAsDouble(nodes[endId]!!)

        if (startId == endId) {
            val total = partialFrom + partialTo
            return if (total <= maxMeters) total else null
        }

        data class Key(val nodeId: Long, val incomingWay: Long?)
        data class QueueNode(val key: Key, val cost: Double)

        val startKey = Key(startId, null)
        val costs = hashMapOf(startKey to 0.0)
        val queue = PriorityQueue<QueueNode>(compareBy { it.cost })
        queue += QueueNode(startKey, 0.0)

        while (queue.isNotEmpty()) {
            val current = queue.remove()
            if (current.cost != costs[current.key]) continue
            if (current.key.nodeId == endId) {
                val total = partialFrom + current.cost + partialTo
                return if (total <= maxMeters) total else null
            }
            if (current.cost + partialFrom > maxMeters) continue
            adjacency[current.key.nodeId].orEmpty().forEach { edge ->
                if (violatesRestriction(current.key.incomingWay, edge.wayId, current.key.nodeId))
                    return@forEach
                val next = Key(edge.toNode, edge.wayId)
                val nextCost = current.cost + edge.distanceMeters
                if (nextCost < (costs[next] ?: Double.MAX_VALUE)) {
                    costs[next] = nextCost
                    queue += QueueNode(next, nextCost)
                }
            }
        }
        return null  // endId never reached
    }

    // ── Full route (time-weighted, unbounded, directed) ──────────────

    fun route(start: GeoPoint, end: GeoPoint): List<GeoPoint>? {
        if (isEmpty) return null
        val startId = nearestNodeAnywhere(start) ?: return null
        val endId = nearestNodeAnywhere(end) ?: return null

        data class Key(val nodeId: Long, val incomingWay: Long?)
        data class QueueNode(val key: Key, val cost: Double)

        val startKey = Key(startId, null)
        val costs = hashMapOf(startKey to 0.0)
        val previous = HashMap<Key, Key>()
        val queue = PriorityQueue<QueueNode>(compareBy { it.cost })
        queue += QueueNode(startKey, 0.0)
        var destination: Key? = null

        while (queue.isNotEmpty()) {
            val current = queue.remove()
            if (current.cost != costs[current.key]) continue
            if (current.key.nodeId == endId) { destination = current.key; break }
            adjacency[current.key.nodeId].orEmpty().forEach { edge ->
                if (violatesRestriction(current.key.incomingWay, edge.wayId, current.key.nodeId))
                    return@forEach
                val next = Key(edge.toNode, edge.wayId)
                val nextCost = current.cost + edge.travelSeconds
                if (nextCost < (costs[next] ?: Double.MAX_VALUE)) {
                    costs[next] = nextCost
                    previous[next] = current.key
                    queue += QueueNode(next, nextCost)
                }
            }
        }

        val endKey = destination ?: return null
        val ids = generateSequence(endKey) { previous[it] }.toList().asReversed().map { it.nodeId }
        return listOf(start) + ids.mapNotNull(nodes::get) + end
    }

    // ── Turn restrictions (identical semantics to OfflineRoadNetwork) ─

    private fun violatesRestriction(fromWay: Long?, toWay: Long, viaNode: Long): Boolean {
        if (fromWay == null) return false
        val local = restrictions.filter { it.fromWay == fromWay && it.viaNode == viaNode }
        return local.any { (!it.only && it.toWay == toWay) || (it.only && it.toWay != toWay) }
    }

    private fun cellId(point: GeoPoint) =
        "${(point.latitude * 100).toInt()}:${(point.longitude * 100).toInt()}"

    companion object {
        val EMPTY = RoadGraph(emptyList(), emptyList())

        internal fun defaultSpeed(highway: String) = when (highway) {
            "motorway", "trunk" -> 80; "primary" -> 60; "secondary" -> 45; else -> 30
        }
    }
}
