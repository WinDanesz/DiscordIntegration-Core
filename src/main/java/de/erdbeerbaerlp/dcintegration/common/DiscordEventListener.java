package de.erdbeerbaerlp.dcintegration.common;

import de.erdbeerbaerlp.dcintegration.common.storage.CommandRegistry;
import de.erdbeerbaerlp.dcintegration.common.discordCommands.DiscordCommand;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.PlayerLinkController;
import de.erdbeerbaerlp.dcintegration.common.util.ComponentUtils;
import de.erdbeerbaerlp.dcintegration.common.util.MessageUtils;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
import de.erdbeerbaerlp.dcintegration.common.util.Variables;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializerOptions;
import dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules;
import dev.vankka.simpleast.core.node.Node;
import dev.vankka.simpleast.core.node.TextNode;
import dev.vankka.simpleast.core.parser.ParseSpec;
import dev.vankka.simpleast.core.parser.Parser;
import dev.vankka.simpleast.core.parser.Rule;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.PatternReplacementResult;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DiscordEventListener implements EventListener {

    public static final MinecraftSerializerOptions mcSerializerOptions;

    static {
        List<Rule<Object, Node<Object>, Object>> rules = new ArrayList<>(DiscordMarkdownRules.createAllRulesForDiscord(false));
        rules.add(new Rule<>(Pattern.compile("(.*)")) {
            @Override
            public ParseSpec<Object, Node<Object>, Object> parse(Matcher matcher, Parser<Object, Node<Object>, Object> parser, Object state) {
                return ParseSpec.createTerminal(new TextNode<>(matcher.group()), state);
            }
        });
        mcSerializerOptions = MinecraftSerializerOptions.defaults().withRules(rules);
    }

    /**
     * Event handler to handle messages
     */
    @Override
    public void onEvent(GenericEvent event) {
        final Discord dc = Variables.discord_instance;
        final JDA jda = dc.getJDA();
        if (jda == null) return;
        if (event instanceof SlashCommandInteractionEvent) {
            SlashCommandInteractionEvent ev = (SlashCommandInteractionEvent) event;
            if (ev.getChannelType().equals(ChannelType.TEXT)) {
                if (CommandRegistry.registeredCMDs.containsKey(ev.getCommandIdLong())) {
                    final DiscordCommand cfCommand = CommandRegistry.registeredCMDs.get(ev.getCommandIdLong());
                    String cmd = cfCommand.getName();
                    String args = ev.getOption("args") != null ? ev.getOption("args").getAsString() : "";
                    processDiscordCommand(ev,ArrayUtils.addAll(new String[]{cmd}, args.split(" ")), ev.getChannel(), ev.getUser(), dc);
                }
            }
        }
        if (event instanceof MessageReactionAddEvent) {
            final MessageReactionAddEvent ev = (MessageReactionAddEvent) event;
            final UUID sender = dc.getSenderUUIDFromMessageID(ev.getMessageId());
            if (ev.getChannel().getId().equals(Configuration.instance().advanced.chatOutputChannelID.equals("default") ? Configuration.instance().general.botChannel : Configuration.instance().advanced.chatOutputChannelID))
                if (sender != Discord.dummyUUID) {
                    if (!PlayerLinkController.getSettings(ev.getUserId(), null).ignoreReactions)
                        dc.srv.sendMCReaction(ev.retrieveMember().complete(), ev.retrieveMessage(), sender, ev.getEmoji());
                }
        }
        if(event instanceof GuildMemberRemoveEvent){
            if(Configuration.instance().linking.unlinkOnLeave && PlayerLinkController.isDiscordLinked(((GuildMemberRemoveEvent) event).getUser().getId()) ){
                PlayerLinkController.unlinkPlayer(((GuildMemberRemoveEvent) event).getUser().getId());
            }
        }
        if (event instanceof MessageReceivedEvent) {
            final MessageReceivedEvent ev = (MessageReceivedEvent) event;
            if (ev.getChannelType().equals(ChannelType.TEXT)) {
                if (!ev.isWebhookMessage() && !ev.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                    if (dc.callEvent((e) -> e.onDiscordMessagePre(ev))) return;
                    if (ev.getChannel().getId().equals(Configuration.instance().advanced.chatInputChannelID.equals("default") ? dc.getChannel().getId() : Configuration.instance().advanced.chatInputChannelID)) {
                        final List<MessageEmbed> embeds = ev.getMessage().getEmbeds();
                        String msg = ev.getMessage().getContentDisplay();
                        msg = MessageUtils.formatEmoteMessage(ev.getMessage().getMentions().getCustomEmojis(), msg);
                        Component attachmentComponent = Component.empty();
                        if (!ev.getMessage().getAttachments().isEmpty()) {
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.newline());
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(Localization.instance().attachment + ":").decorate(TextDecoration.UNDERLINED));
                        }
                        for (Message.Attachment a : ev.getMessage().getAttachments()) {
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.newline());
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(a.getFileName()).decorate(TextDecoration.UNDERLINED).color(TextColor.color(0x06, 0x45, 0xAD)).clickEvent(ClickEvent.openUrl(a.getUrl())));
                        }
                        for (MessageEmbed e : embeds) {
                            if (e.isEmpty()) continue;
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text("\n-----["+Localization.instance().embed +"]-----\n"));
                            if (e.getAuthor() != null && e.getAuthor().getName() != null && !e.getAuthor().getName().trim().isEmpty()) {
                                attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(e.getAuthor().getName() + "\n").decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC));
                            }
                            if (e.getTitle() != null && !e.getTitle().trim().isEmpty()) {
                                attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(e.getTitle() + "\n").decorate(TextDecoration.BOLD));
                            }
                            if (e.getDescription() != null && !e.getDescription().trim().isEmpty()) {
                                attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(Localization.instance().embedMessage+":\n" + e.getDescription() + "\n"));
                            }
                            if (e.getImage() != null && e.getImage().getUrl() != null && !e.getImage().getUrl().isEmpty()) {
                                attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text(Localization.instance().embedImage+": " + e.getImage().getUrl() + "\n"));
                            }
                            attachmentComponent = ComponentUtils.append(attachmentComponent, Component.text("\n-----------------"));
                        }
                        for(Sticker s : ev.getMessage().getStickers())
                            attachmentComponent = ComponentUtils.append(attachmentComponent,Component.text("\n"+Localization.instance().sticker+": "+s.getName()));

                        Component outMsg = MinecraftSerializer.INSTANCE.serialize(msg.replace("\n", "\\n"), mcSerializerOptions);
                        final Message reply = ev.getMessage().getReferencedMessage();
                        final boolean hasReply = reply != null;
                        Component out = LegacyComponentSerializer.legacySection().deserialize(hasReply ? Localization.instance().ingame_discordReplyMessage : Localization.instance().ingame_discordMessage);
                        final int memberColor = (ev.getMember() != null ? ev.getMember().getColorRaw() : 0);
                        final TextReplacementConfig msgReplacer = ComponentUtils.replaceLiteral("%msg%", ComponentUtils.makeURLsClickable(outMsg.replaceText(ComponentUtils.replaceLiteral("\\n", Component.newline()))));
                        final TextReplacementConfig idReplacer = ComponentUtils.replaceLiteral("%id%", ev.getAuthor().getId());
                        Component user = Component.text((ev.getMember() != null ? ev.getMember().getEffectiveName() : ev.getAuthor().getName())).style(Style.style(TextColor.color(memberColor))
                                        .clickEvent(ClickEvent.suggestCommand("<@" + ev.getAuthor().getId() + ">"))
                                        .hoverEvent(HoverEvent.showText(Component.text(Localization.instance().discordUserHover.replace("%user#tag%", ev.getAuthor().getAsTag()).replace("%user%", ev.getMember() == null ? ev.getAuthor().getName() : ev.getMember().getEffectiveName()).replace("%id%", ev.getAuthor().getId())))));
                        if(ev.getAuthor().isBot()){
                            user = ComponentUtils.append(user,Component.text("[BOT]").style(Style.style(TextColors.DISCORD_BLURPLE).hoverEvent(HoverEvent.showText(Component.text(Localization.instance().bot)))));
                        }
                        final TextReplacementConfig userReplacer = ComponentUtils.replaceLiteral("%user%", user);
                        out = out.replaceText(userReplacer).replaceText(idReplacer).replaceText(msgReplacer);
                        if (hasReply) {
                            final Component repUser = Component.text((reply.getMember() != null ? reply.getMember().getEffectiveName() : reply.getAuthor().getName()))
                                    .style(ComponentUtils.addUserHoverClick(Style.style(TextColor.color((reply.getMember() != null ? reply.getMember().getColorRaw() : 0))), reply.getAuthor(), reply.getMember()));
                            out = out.replaceText(ComponentUtils.replaceLiteral("%ruser%", repUser));
                            final String repMsg = MessageUtils.formatEmoteMessage(reply.getMentions().getCustomEmojis(), reply.getContentDisplay());
                            final Component replyMsg = MinecraftSerializer.INSTANCE.serialize(repMsg.replace("\n", "\\n"), mcSerializerOptions);
                            out = out.replaceText(ComponentUtils.replaceLiteral("%rmsg%", ComponentUtils.makeURLsClickable(replyMsg.replaceText(ComponentUtils.replaceLiteral("\\n", Component.newline())))));

                        }
                        out = ComponentUtils.append(out, attachmentComponent);
                        dc.srv.sendMCMessage(out);
                    }
                    dc.callEventC((e) -> e.onDiscordMessagePost(ev));
                }
            }
        }
    }

    private void processDiscordCommand(SlashCommandInteractionEvent ev, final String[] command, final MessageChannelUnion channel, User sender, final Discord dc) {
        boolean hasPermission = true;
        boolean executed = false;
        ReplyCallbackAction replyCallbackAction = ev.deferReply();
        for (final DiscordCommand cmd : CommandRegistry.getCommandList()) {
            if (cmd.getName().equals(command[0])) {
                if (cmd.canUserExecuteCommand(sender)) {
                    if (dc.callEvent((e) -> e.onDiscordCommand(channel, sender, cmd))) return;
                    cmd.execute(ev,replyCallbackAction);
                    executed = true;
                } else {
                    hasPermission = false;
                }
            }

        }
        if (!executed)
            if (dc.callEvent((e) -> e.onDiscordCommand(channel, sender, null))) return;
        if (!hasPermission) {
            replyCallbackAction.setContent(Localization.instance().commands.noPermission).setEphemeral(true).queue();
            return;
        }
        if (!executed && (Configuration.instance().commands.showUnknownCommandEverywhere || channel.getId().equals(dc.getChannel().getId())) && Configuration.instance().commands.showUnknownCommandMessage) {
            if (Configuration.instance().commands.helpCmdEnabled)
                replyCallbackAction.setContent(Localization.instance().commands.unknownCommand.replace("%prefix%", "/")).setEphemeral(true).queue();
        }

    }
}
