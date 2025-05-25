package me.lukiiy.tethered

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object Cmd {
    private val main = Commands.literal("tethered")
        .requires { it.sender.hasPermission("tethered.cmd") }
        .executes {
            it.source.sender.sendMessage(Component.text("Tethered Reload complete!").color(NamedTextColor.GREEN))
            Tethered.getInstance().reloadConfig()
            Tethered.getInstance().loadVariables()
            Command.SINGLE_SUCCESS
        }

    fun register(): LiteralCommandNode<CommandSourceStack> = main.build()
}