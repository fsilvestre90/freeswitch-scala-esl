import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import esl.FSConnection.FSData
import esl.InboundServer
import esl.domain.EventNames.{All, ChannelAnswer}
import esl.domain.{ApplicationCommandConfig, EventMessage}
import org.apache.logging.log4j.scala.Logging

import scala.util.{Failure, Success}


object InboundTest extends App with Logging {
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()
  implicit val ec = system.dispatcher


  InboundServer("localhost", 8021).connect("ClueCon") {
    fsSocket =>
      /** Inbound fsConnection future will get completed when client is authorised by freeswitch */
      fsSocket.onComplete {
        case Success(socket) =>
          socket.fsConnection.subscribeEvents(All).foreach {
            commandResponse =>
              commandResponse.commandReply.foreach(f => logger.info(f))
          }
        case Failure(ex) => logger.info("failed to make inbound socket connection", ex)
      }
      Sink.foreach[FSData] { fsData =>
        /**Here you have an access of fs connection along with fs messages*/
        fsData.fsMessages.collect {
          case event: EventMessage if event.eventName.contains(ChannelAnswer) =>
            val uuid = event.uuid.getOrElse("")
            fsData.fSConnection.play("/usr/share/freeswitch/sounds/en/us/callie/conference/8000/conf-pin.wav",
              ApplicationCommandConfig(uuid)).foreach {
              commandResponse =>

                /** This future will get complete, when FS send command/reply message to the socket */
                commandResponse.commandReply.foreach(f => logger.info(s"Got command reply: ${f}"))

                /** This future will get complete, when FS send CHANNEL_EXECUTE event to the socket */
                commandResponse.executeEvent.foreach(f => logger.info(s"Got ChannelExecute event: ${f}"))

                /** This future will get complete, when FS send CHANNEL_EXECUTE_COMPLETE  event to the socket */
                commandResponse.executeComplete.foreach(f => logger.info(s"ChannelExecuteComplete event: ${f}"))
            }
        }
        logger.info(fsData.fsMessages)
      }
  }.onComplete {
    case Success(result) => logger.info(s"Inbound socket started successfully ${result}")
    case Failure(ex) => logger.info(s"Inbound socket failed to start with exception ${ex}")
  }
}