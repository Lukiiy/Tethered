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
    private val tickTask: Consumer<ScheduledTask> = Consumer { _ ->
        val available = server.onlinePlayers.filter { isValidPlayer(it) }.toMutableList()
        val paired = mutableSetOf<Player>()

        for (player in available) {
            if (player in paired) continue

            val other = getNearestPlayer(player, paired) ?: continue

            paired += player
            paired += other

            val group = listOf(player, other)
            val center = group.map { it.location.toVector() }
                .reduce { acc, v -> acc.add(v) }
                .multiply(.5)

            for (p in group) {
                val pVec = p.location.toVector()
                val dist = pVec.distance(center)
                if (dist <= dragDist) continue

                val speed = ((dist - dragDist) / dragDist).coerceIn(.5, 2.0) / 2

                if (p.isInsideVehicle) p.leaveVehicle()
                if (p.isSleeping) p.damage(0.1)

                val dir = center.clone().subtract(pVec).normalize().multiply(speed)

                if (p.location.y < center.y) dir.y += 0.1

                p.velocity = dir
                rayParticle(p.location.add(0.0, p.boundingBox.height / 2, 0.0), center.toLocation(p.world).add(.0, .5, .0))
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

    private fun getNearestPlayer(p: Player, exclude: Set<Player> = emptySet()): Player? = p.world.players.asSequence().filter { it != p && it !in exclude && isValidPlayer(it) }.minByOrNull { it.location.distanceSquared(p.location) }

    val isValidPlayer = { p: Player -> !p.gameMode.isInvulnerable && !p.isDead }

    @EventHandler
    fun death(e: PlayerDeathEvent) {
        val p = e.player
        if (p.gameMode.isInvulnerable) return

        e.player.scheduler.run(this, {
            getNearestPlayer(p)?.apply {
                damage(.1, e.damageSource)
                health = .0
            }
        }, null)
    }

    @EventHandler
    fun kick(e: PlayerKickEvent) {
        if (e.cause == PlayerKickEvent.Cause.FLYING_PLAYER) e.isCancelled = true
    }
}
