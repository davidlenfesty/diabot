package com.dongtronic.diabot.platforms.discord.commands.nightscout

import com.dongtronic.diabot.data.mongodb.NightscoutDAO
import com.dongtronic.diabot.graph.PlottingStyle
import com.dongtronic.diabot.platforms.discord.commands.ApplicationCommand
import com.dongtronic.diabot.platforms.discord.logic.NightscoutFacade
import com.dongtronic.diabot.util.logger
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.interactions.components.Button

class NightscoutApplicationCommand : ApplicationCommand {
    private val logger = logger()

    private val groupNameSet = "set"
    private val groupNameClear = "clear"
    private val groupNameGet = "get"
    private val commandModeUrl = "url"
    private val commandModeToken = "token"
    private val commandModeAll = "all"
    private val commandModePrivacy = "privacy"
    private val commandModeGlobalPrivacy = "globalprivacy"
    private val commandModeGraphMode = "graphmode"
    private val commandModeGraphHours = "graphhours"
    private val commandArgUrl = "url"
    private val commandArgToken = "token"
    private val commandArgPrivacy = "privacy"
    private val commandArgPublic = "public"
    private val commandArgPrivate = "private"

    private val commandArgMode = "mode"
    private val commandArgScatter = "scatter"
    private val commandArgLine = "line"

    private val commandArgHours = "hours"

    private val commandButtonDeleteConfirm = "nsdeleteyes"
    private val commandButtonDeleteCancel = "nsdeleteno"

    override val commandName: String = "nightscout"
    override val buttonIds: Set<String> = setOf(commandButtonDeleteConfirm, commandButtonDeleteCancel)

    override fun execute(event: SlashCommandEvent) {
        when (event.subcommandGroup) {
            groupNameSet -> when (event.subcommandName) {
                commandModeToken -> setToken(event)
                commandModeUrl -> setUrl(event)
                commandModePrivacy -> setPrivacy(event)
                commandModeGlobalPrivacy -> setGlobalPrivacy(event)
                commandModeGraphMode -> setGraphMode(event)
                commandModeGraphHours -> setGraphHours(event)
            }
            groupNameClear -> when (event.subcommandName) {
                commandModeToken -> clearToken(event)
                commandModeUrl -> clearUrl(event)
                commandModeAll -> confirmDeleteData(event)
            }
            groupNameGet -> when (event.subcommandName) {
                commandModeUrl -> getUrl(event)
                commandModeToken -> getToken(event)
            }
        }
    }

    override fun execute(event: ButtonClickEvent) {
        when (event.componentId) {
            commandButtonDeleteConfirm -> deleteData(event)
            commandButtonDeleteCancel -> cancelDeleteData(event)
        }
    }

    private fun setToken(event: SlashCommandEvent) {
        NightscoutFacade.setToken(event.user, event.getOption(commandArgToken)!!.asString).subscribe({
            event.reply("Your Nightscout token was set").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error setting your Nightscout token, please try again later.")
        })
    }

    private fun setUrl(event: SlashCommandEvent) {
        val url = event.getOption(commandArgUrl)!!.asString
        NightscoutFacade.setUrl(event.user, url).subscribe({
            event.reply("Your Nightscout URL was set to $url").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error while setting your Nightscout URL. Please try again later.")
        })
    }

    private fun setPrivacy(event: SlashCommandEvent) {
        if (!event.isFromGuild) {
            warnGuildOnly(event)
            return
        }

        val privacy = event.getOption(commandArgPrivacy)!!.asString

        val public = commandArgPublic == privacy
        val visibility = if (public) "public" else "private"

        NightscoutFacade.setPublic(event.user, event.guild!!, public).subscribe({
            event.reply("Your Nightscout data was made $visibility in this server").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error while setting your Nightscout data to $visibility in this server. Please try again later.")
        })
    }

    private fun setGlobalPrivacy(event: SlashCommandEvent) {
        val privacy = event.getOption(commandArgPrivacy)!!.asString

        val public = commandArgPublic == privacy
        val visibility = if (public) "public" else "private"

        if (public) {
            event.reply("You must set your Nightscout data to public on a per-server basis.").setEphemeral(true).queue()
        }

        NightscoutFacade.setGlobalPublic(event.user, public).subscribe({
            event.reply("Your Nightscout data has been set to $visibility in all servers").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error setting your global Nightscout privacy setting. Please try again later")
        })
    }

    private fun setGraphMode(event: SlashCommandEvent) {
        val mode = event.getOption(commandArgMode)!!.asString

        val plottingStyle = PlottingStyle.values().first { it.name.startsWith(mode, true) }

        NightscoutDAO.instance.getUser(event.user.id)
                .map { it.graphSettings }
                .map { it.copy(plotMode = plottingStyle) }
                .flatMap { NightscoutDAO.instance.updateGraphSettings(event.user.id, it) }
                .subscribe({
                    event.reply("Plotting style changed to `${it.plotMode.name}`").setEphemeral(true).queue()
                }, {
                    replyError(event, it, "Could not update plotting style: ${it.javaClass.simpleName}")
                    logger.warn("Unexpected error when changing graph mode for ${event.user}", it)
                })
    }

    private fun setGraphHours(event: SlashCommandEvent) {
        val hours = event.getOption(commandArgHours)!!.asLong

        if (hours < 1 || hours > 24) {
            event.reply("The number of hours must be between 1 and 24").setEphemeral(true).queue()
            return
        }

        NightscoutDAO.instance.getUser(event.user.id)
                .map { it.graphSettings.copy(hours = hours) }
                .flatMap { NightscoutDAO.instance.updateGraphSettings(event.user.id, it) }
                .subscribe({
                    val plural = if (it.hours != 1L) "s" else ""
                    event.reply("Your future graphs will now display ${it.hours} hour$plural of data").setEphemeral(true).queue()
                }, {
                    replyError(event, it, "Could not change the graph hours: ${it.javaClass.simpleName}")
                    logger.warn("Unexpected error when changing graph hours for ${event.user}", it)
                })
    }

    private fun clearToken(event: SlashCommandEvent) {
        NightscoutFacade.clearToken(event.user).subscribe({
            event.reply("Your Nightscout token has been deleted").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error deleting your Nightscout token")
        })
    }

    private fun clearUrl(event: SlashCommandEvent) {
        NightscoutFacade.clearUrl(event.user).subscribe({
            event.reply("Your Nightscout URL has been deleted").setEphemeral(true).queue()
        }, {
            replyError(event, it, "There was an error while removing your Nightscout URL. Please try again later.")
        })
    }

    private fun getUrl(event: SlashCommandEvent) {
        NightscoutFacade.getUser(event.user).subscribe {
            if (it.url != null) {
                event.reply("Your configured Nightscout URL is `${it.url}`").setEphemeral(true).queue()
            } else {
                event.reply("You do not have a configured Nightscout URL. Use `/nightscout set url` to configure it.").setEphemeral(true).queue()
            }
        }
    }

    private fun getToken(event: SlashCommandEvent) {
        NightscoutFacade.getUser(event.user).subscribe {
            if (it.token != null) {
                event.reply("Your configured Nightscout token is `${it.token}`").setEphemeral(true).queue()
            } else {
                event.reply("You do not have a configured Nightscout token. Use `/nightscout set token` to configure it.").setEphemeral(true).queue()
            }
        }
    }

    private fun confirmDeleteData(event: SlashCommandEvent) {
        event.reply("Are you sure you wish to **delete** your Nightscout settings?\n**This will remove all your Nightscout settings**")
                .addActionRow(
                        Button.danger(commandButtonDeleteConfirm, "Yes, delete all settings"),
                        Button.secondary(commandButtonDeleteCancel, "Cancel")
                ).setEphemeral(true).queue()
    }

    private fun deleteData(event: ButtonClickEvent) {
        NightscoutFacade.clearAll(event.user).subscribe({
            event.editMessage("Your Nightscout settings have been deleted").setActionRow(
                    Button.danger(commandButtonDeleteConfirm, "Yes, delete all settings").asDisabled(),
                    Button.secondary(commandButtonDeleteCancel, "Cancel").asDisabled()
            ).queue()
        }, {
            replyError(event, it, "There was an error while removing your Nightscout settings. Please try again later.")
        })
    }

    private fun cancelDeleteData(event: ButtonClickEvent) {
        event.editMessage("Your Nightscout settings were **not** deleted.").setActionRow(
                Button.danger(commandButtonDeleteConfirm, "Yes, delete all settings").asDisabled(),
                Button.secondary(commandButtonDeleteCancel, "Cancel").asDisabled()
        ).queue()
    }

    private fun warnGuildOnly(event: SlashCommandEvent) {
        event.reply("You must use this command in a server").setEphemeral(true).queue()
    }

    override fun config(): CommandData {
        return CommandData(commandName, "Manage your Nightscout settings").addSubcommandGroups(
                SubcommandGroupData(groupNameSet, "Set Nightscout settings").addSubcommands(
                        SubcommandData(commandModeUrl, "Set Nightscout url")
                                .addOption(OptionType.STRING, commandArgUrl, "URL of your Nightscout instance", true),
                        SubcommandData(commandModeToken, "Set Nightscout token")
                                .addOption(OptionType.STRING, commandArgToken, "The authentication token of your Nightscout instance", true),
                        SubcommandData(commandModePrivacy, "Set Nightscout privacy setting in this server")
                                .addOptions(OptionData(OptionType.STRING, commandArgPrivacy, "Privacy setting", true)
                                        .addChoice(commandArgPrivate, commandArgPrivate)
                                        .addChoice(commandArgPublic, commandArgPublic)),
                        SubcommandData(commandModeGlobalPrivacy, "Set Nightscout privacy setting in all servers")
                                .addOptions(OptionData(OptionType.STRING, commandArgPrivacy, "Privacy setting", true)
                                        .addChoice(commandArgPrivate, commandArgPrivate)),
                        SubcommandData(commandModeGraphMode, "Set the plotting style for Nightscout graphs")
                                .addOptions(OptionData(OptionType.STRING, commandArgMode, "Plotting style", true)
                                        .addChoice(commandArgScatter, commandArgScatter)
                                        .addChoice(commandArgLine, commandArgLine)),
                        SubcommandData(commandModeGraphHours, "Set the number of hours displayed in Nightscout graphs")
                                .addOptions(OptionData(OptionType.INTEGER, commandArgHours, "Hours", true))

                ),
                SubcommandGroupData(groupNameClear, "Clear Nightscout settings").addSubcommands(
                        SubcommandData(commandModeUrl, "Clear Nightscout url"),
                        SubcommandData(commandModeToken, "Clear Nightscout token"),
                        SubcommandData(commandModeAll, "Clear all Nightscout data")
                ),
                SubcommandGroupData(groupNameGet, "Get Nightscout settings (private)").addSubcommands(
                        SubcommandData(commandModeUrl, "Get Nightscout URL"),
                        SubcommandData(commandModeToken, "Get Nightscout token")
                )
        )
    }
}
