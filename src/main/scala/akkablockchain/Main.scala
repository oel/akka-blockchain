package akkablockchain

import akkablockchain.util.Util._
import akkablockchain.util.Crypto._
import akkablockchain.actor._

import akka.actor.ActorSystem
import akka.cluster.Cluster

import com.typesafe.config.ConfigFactory
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}

object Main {

  def main(args: Array[String]): Unit = {

    val (port: Int, minerKeyFile: String, test: Boolean) =
      if (args.isEmpty) {
        (2551, "src/main/resources/keys/account0_public.pem", true)
      } else if (args.length < 2) {
        Console.err.println("[Main] ERROR: Arguments port# and minerKeyFile required! e.g. 2552 src/main/resources/keys/account1_public.pem [test]")
        sys.exit(1)
      } else {
        Try((args(0).toInt, args(1))) match {
          case Success((port, keyFile)) if Files.exists(Paths.get(keyFile)) =>
            (port, keyFile, args.length >= 3 && args(2).toLowerCase.contains("test"))
          case _ =>
            Console.err.println(s"[Main] ERROR: Either port# $args(0) isn't an integer or minerKeyFile $args(1) doesn't exist!")
            sys.exit(1)
        }
      }

    val minerAccountKey = publicKeyFromPemFile(minerKeyFile) match {
      case Some(key) =>
        publicKeyToBase64(key)
      case None =>
        Console.err.println(s"[Main] ERROR: Problem in getting public key from minerKeyFile!")
        sys.exit(1)
    }

    val conf = ConfigFactory.parseString("akka.remote.artery.canonical.port = " + port).
      withFallback(ConfigFactory.load())

    val timeoutMining = conf.getLong("blockchain.timeout.mining")
    val timeoutValidation = conf.getLong("blockchain.timeout.block-validation")

    val transFeedInterval = conf.getLong("blockchain.simulator.transaction-feed-interval")
    val miningAvgInterval = conf.getLong("blockchain.simulator.mining-average-interval")

    implicit val system = ActorSystem("blockchain", conf)

    val cluster: Cluster = Cluster(system)

    val blockchainer = system.actorOf(Blockchainer.props(minerAccountKey, timeoutMining, timeoutValidation), "blockchainer")

    val simulator = system.actorOf(Simulator.props(blockchainer, transFeedInterval, miningAvgInterval), "simulator")

    if (test)
      simulator ! Simulator.QuickTest
    else
      simulator ! Simulator.MiningLoop
  }
}
