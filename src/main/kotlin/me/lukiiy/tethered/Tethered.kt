package me.lukiiy.tethered

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

class Tethered : JavaPlugin(), Listener {
    var maxDist = 6.0
    var dragDist = maxDist / 2
    var maxGroup = 2

    override fun onEnable() {
        setupConfig()
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runAtFixedRate(this, tickTask, 1L, 2L)
        loadVariables()

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(Cmd.register(), "Tethered's main command!")
        }
    }

    companion object {
        fun getInstance(): Tethered = getPlugin(Tethered::class.java)
    }

    // Config
    fun setupConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
    }

    fun loadVariables() {
        maxDist = config.getDouble("max_distance")
        dragDist = config.getDouble("drag_distance")
        maxGroup = config.getInt("max_group_size")
    }

    // Stuff
    private val tickTask: Consumer<ScheduledTask> = Consumer { task ->
        for (player in server.onlinePlayers.filter(isValidPlayer)) {
            val others = getGroupPlayers(player)
            if (others.isEmpty()) continue

            val group = listOf(player) + others

            val center = group.map { it.location.toVector() }
                .reduce { acc, vec -> acc.add(vec) }
                .multiply(1.0 / group.size)

            for (p in group) {
                val pLocation = p.location.toVector()
                val dist = pLocation.distance(center)

                if (dist > dragDist) {
                    val distRelative = (dist - dragDist) / (maxDist - dragDist)
                    val speed = .5 * distRelative.coerceIn(.75, 2.0)

                    if (p.isInsideVehicle) p.leaveVehicle()
                    p.velocity = center.clone().subtract(pLocation).normalize().multiply(speed).apply { y += 0.1 }

                    for (other in group) {
                        if (other == p || listOf(p, other).random() != p) continue // Choose 1 to draw
                        rayParticle(p.location.add(0.0, p.boundingBox.height / 2, 0.0), other.location.add(0.0, other.boundingBox.height / 2, 0.0))
                    }
                }
            }
        }
    }

    fun rayParticle(from: Location, to: Location) {
        val rayDir = to.toVector().subtract(from.toVector())
        val stepVec = rayDir.clone().normalize().multiply(.5)
        val steps = (rayDir.length() * 2.0).toInt().coerceAtMost(25)

        var point = from.toVector()
        repeat(steps) {
            from.world.spawnParticle(Particle.WAX_OFF, point.toLocation(from.world), 1)
            point.add(stepVec)
        }
    }

    val isValidPlayer = { p: Player -> !p.gameMode.isInvulnerable && !p.isDead }

    fun getGroupPlayers(player: Player): List<Player> {
        return player.world.players.asSequence()
            .filter { it != player && isValidPlayer(it) }
            .sortedBy { it.location.distanceSquared(player.location) }
            .take(maxGroup - 1)
            .toList()
    }

    @EventHandler
    fun death(e: PlayerDeathEvent) {
        val player = e.player
        if (player.gameMode.isInvulnerable) return

        val group = getGroupPlayers(player)
        if (group.isEmpty()) return

        for (player in group) player.health = 0.0
    }
}
