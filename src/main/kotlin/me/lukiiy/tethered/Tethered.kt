package me.lukiiy.tethered

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

class Tethered : JavaPlugin(), Listener {
    var dragDist = 4.0

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
        dragDist = config.getDouble("dragDistance", dragDist)
    }

    // Stuff
    private val tickTask: Consumer<ScheduledTask> = Consumer { task ->
        val players = server.onlinePlayers.filter { isValidPlayer(it) }
        if (players.isEmpty()) return@Consumer

        for (player in players) {
            val others = getNearestPlayer(player)?.let { listOf(it) } ?: continue
            val group = listOf(player) + others

            val center = group.map { it.location.toVector() }.reduce { acc, vec -> acc.add(vec) }.multiply(1.0 / group.size)

            for (p in group) {
                val pVec = p.location.toVector()
                val dist = pVec.distance(center)
                if (dist <= dragDist) continue

                var speed = .5 * ((dist - dragDist) / dragDist).coerceIn(0.75, 2.0)

                if (p.isInsideVehicle) p.leaveVehicle()

                p.velocity = center.clone().subtract(pVec).normalize().multiply(speed).apply { y += if (p.location.y < player.location.y) 0.1 else 0.0 }
                if (p.world.fullTime % 2L == 0L) rayParticle(p.location.add(0.0, p.boundingBox.height / 2, 0.0), player.location.add(0.0, player.boundingBox.height / 2, 0.0))
            }
        }
    }

    fun rayParticle(from: Location, to: Location) {
        val rayDir = to.toVector().subtract(from.toVector())
        val stepVec = rayDir.clone().normalize().multiply(.5)
        val steps = (rayDir.length() * 3.0).toInt().coerceAtMost(25)

        var point = from.toVector()
        repeat(steps) {
            from.world.spawnParticle(Particle.WAX_OFF, point.toLocation(from.world), 1)
            point.add(stepVec)
        }
    }

    private fun getNearestPlayer(p: Player): Player? {
        val pLoc = p.location

        return p.world.players.asSequence().filter { it != p && isValidPlayer(it) }.minByOrNull { it.location.distanceSquared(pLoc) }
    }

    val isValidPlayer = { p: Player -> !p.gameMode.isInvulnerable && !p.isDead }

    @EventHandler
    fun death(e: PlayerDeathEvent) {
        val p = e.player
        if (p.gameMode.isInvulnerable) return

        e.player.scheduler.run(this, {
            getNearestPlayer(p)?.damage(999.0, e.damageSource)
        }, null)
    }

    @EventHandler
    fun kick(e: PlayerKickEvent) {
        if (e.cause == PlayerKickEvent.Cause.FLYING_PLAYER) e.isCancelled = true
    }
}
