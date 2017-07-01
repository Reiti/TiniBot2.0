package science.wasabi.tini.bot.discord


import akka.typed.scaladsl.Actor
import net.dv8tion.jda.core._
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import science.wasabi.tini.config.Config.TiniConfig
import DiscordWrapperConverter._

/**
  * A way to add listeners as lambdas
  * @param listener
  * @param config
  */
class JdaIngestion(listener: DiscordMessage => Unit)(implicit config: TiniConfig) {

  val jda: JDA =
    new JDABuilder(AccountType.BOT)
      .setToken(config.discordBotToken)
      .addEventListener(new ListenerAdapter {
        override def onReady(event: ReadyEvent): Unit = println("READY")
        override def onGuildMessageReceived(event: GuildMessageReceivedEvent): Unit = {
          listener(event.getMessage)
        }
        override def onPrivateMessageReceived(event: PrivateMessageReceivedEvent): Unit = {
          listener(event.getMessage)
        }
      })
      .buildAsync()
}


object JdaIngestionActor {

  import akka.typed._

  trait Commands
  case class Ready(jda: JDA) extends Commands
  case class Shutdown() extends Commands

  def supervisor(jda: JDA): Behavior[Commands] =
    Actor.immutable{ (ctx, message) =>
      message match {
        case event: Shutdown =>
          println("Shutting down")
          Actor.stopped
      }
    }

  def starting(implicit messageHandler: Behavior[DiscordMessage]): Behavior[Commands] =
    Actor.immutable { (ctx, message) =>
      message match {
        case event: Ready =>
          println("JDA is ready")

          val handler = ctx.spawn(messageHandler, "handler")

          event.jda.addEventListener(new ListenerAdapter {
            override def onGuildMessageReceived(event: GuildMessageReceivedEvent): Unit = {
              handler ! event.getMessage
            }
            override def onPrivateMessageReceived(event: PrivateMessageReceivedEvent): Unit = {
              handler ! event.getMessage
            }
          })
          supervisor(event.jda)
      }
    }

  /**
    * A way to spawn an actor system to listen for messages.
    * @param messageHandler
    * @param config
    */
  def startup(implicit messageHandler: Behavior[DiscordMessage], config: TiniConfig) = {
    val system: ActorSystem[Commands] = ActorSystem("jdaActor", starting)

    val jda = new JDABuilder(AccountType.BOT)
      .setToken(config.discordBotToken)
      .addEventListener(new ListenerAdapter {
        override def onReady(event: ReadyEvent): Unit = system ! Ready(event.getJDA)
      })
      .buildAsync()
  }
}