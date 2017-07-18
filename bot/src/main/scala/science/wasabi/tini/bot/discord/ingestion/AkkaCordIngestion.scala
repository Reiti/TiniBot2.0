package science.wasabi.tini.bot.discord.ingestion


import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.EventStream
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import net.katsstuff.akkacord.{APIMessage, DiscordClientSettings}
import science.wasabi.tini.bot.discord.wrapper.{DiscordMessage, DiscordWrapperConverter}
import science.wasabi.tini.config.Config.TiniConfig
import science.wasabi.tini.bot.discord.wrapper.DiscordWrapperConverter.AkkaCordConverter._


class AkkaCordIngestion(implicit config: TiniConfig) {
  implicit val system: ActorSystem = ActorSystem("AkkaCord")
  implicit val materializer = ActorMaterializer()

  private val eventStream = new EventStream(system)

  val client: ActorRef = DiscordClientSettings(token = config.discordBotToken, system = system, eventStream = eventStream).connect

  private val (ref, publisher) = Source.actorRef[APIMessage.MessageCreate](32, OverflowStrategy.dropHead)
    .map(in => DiscordWrapperConverter.AkkaCordConverter.convertMessage(in.message))
    .toMat(Sink.asPublisher(true))(Keep.both)
    .run()

  eventStream.subscribe(ref, classOf[APIMessage.MessageCreate])

  val source: Source[DiscordMessage, NotUsed] = Source.fromPublisher(publisher)
}
