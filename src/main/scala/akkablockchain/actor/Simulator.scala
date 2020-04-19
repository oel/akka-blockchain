package akkablockchain.actor

import akkablockchain.model._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Simulator {
  def props(blockchainer: ActorRef, transFeedInterval: Long, miningAvgInterval: Long): Props =
    Props(classOf[Simulator], blockchainer, transFeedInterval, miningAvgInterval)

  case object QuickTest
  case object MiningLoop
  case object Tick
  case object Tock
}

class Simulator(blockchainer: ActorRef, transFeedInterval: Long, miningAvgInterval: Long) extends Actor with ActorLogging {
  import Simulator._

  implicit val ec: ExecutionContext = context.dispatcher

  val feeder = context.actorOf(TransactionFeeder.props(blockchainer, transFeedInterval.millis), "feeder")

  override def receive: Receive = {
    case MiningLoop =>
      feeder ! TransactionFeeder.Run

      Thread.sleep(transFeedInterval * 2)

      context.become(scheduleMining)
      self ! Tick

    case QuickTest =>
      log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue
      blockchainer ! Blockchainer.GetBlockchain

      feeder ! TransactionFeeder.Run

      Thread.sleep(miningAvgInterval)  // Initial time for populating the empty queue

      log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue
      blockchainer ! Blockchainer.GetBlockchain

      log.info("[QuickTest] Mining #1 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(10)  // To trigger BusyException

      log.info("[QuickTest] Mining #2 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(miningAvgInterval)

      log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue
      blockchainer ! Blockchainer.GetBlockchain

      log.info("[QuickTest] Mining #3 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(miningAvgInterval)

      feeder ! TransactionFeeder.StopRunning

      log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue
      blockchainer ! Blockchainer.GetBlockchain

    case queue: Queue[_] =>
      log.info(s"[QuickTest] Transaction queue: ${queue}")

    case block: Block =>
      log.info(s"[QuickTest] Blockchain: ${blockchainToList(block)}")
  }

  def scheduleMining: Receive = {
    case Tick =>
      val interval = randomInterval(miningAvgInterval, 0.3)
      context.system.scheduler.scheduleOnce(interval.millis, self, Tock)
      context.become(waitingToMine)

      log.info(s"[MiningLoop] Start mining in ${interval} millis")
      log.info("[MiningLoop] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue
      blockchainer ! Blockchainer.GetBlockchain

    case _ =>
      // Do nothing
  }

  def waitingToMine: Receive = {
    case Tock =>
      blockchainer ! Blockchainer.Mining
      context.become(scheduleMining)
      self ! Tick

    case queue: Queue[_] =>
      log.info(s"[MiningLoop] Transaction queue: ${queue}")

    case block: Block =>
      log.info(s"[MiningLoop] Blockchain: ${blockchainToList(block)}")

    case _ =>
      // Do nothing
  }

  private def blockchainToList(block: Block): List[Block] = {
    @scala.annotation.tailrec
    def loop(blk: Block, ls: List[Block]): List[Block] = blk match {
      case b: LinkedBlock => loop(b.blockPrev, b :: ls)
      case b: RootBlock.type => b :: ls
    }
    loop(block, List.empty[Block]).reverse
  }

  private def randomFcn = java.util.concurrent.ThreadLocalRandom.current

  private def randomInterval(average: Long, delta: Double): Long = {
    val rand = randomFcn.nextDouble(average * (1.0 - delta), average * (1.0 + delta))
    Math.round(rand / 1000) * 1000
  }
}
