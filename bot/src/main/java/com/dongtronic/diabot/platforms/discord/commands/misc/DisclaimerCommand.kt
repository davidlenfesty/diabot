package com.dongtronic.diabot.platforms.discord.commands.misc

import com.dongtronic.diabot.platforms.discord.commands.DiscordCommand
import com.dongtronic.diabot.util.logger
import com.jagrosh.jdautilities.command.CommandEvent
import com.jagrosh.jdautilities.command.CooldownScope

class DisclaimerCommand(category: Category) : DiscordCommand(category, null) {

    private val logger = logger()

    init {
        this.name = "disclaimer"
        this.help = "Show the disclaimer for diabot"
        this.guildOnly = false
        this.ownerCommand = false
        this.hidden = false
        this.cooldown = 180
        this.cooldownScope = CooldownScope.CHANNEL
    }

    override fun execute(event: CommandEvent) {
        val text = this::class.java.classLoader.getResource("DISCLAIMER")?.readText()

        event.reply(text)
    }
}
