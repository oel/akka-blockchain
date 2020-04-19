package akkablockchain.actor

import akkablockchain.util.Util._
import akkablockchain.model.ProofOfWork.{defaultDifficulty, defaultNonce}
import akkablockchain.model._

import akka.actor.{Actor, ActorLogging, Props, ActorRef, Status}

import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.Try

object Miner {
  def props(accountKey: String, timeoutPoW: Long): Props = Props(classOf[Miner], accountKey, timeoutPoW)

  sealed trait Mining
  case class Mine(blockPrev: Block, trans: Transactions) extends Mining
  case object DoneMining extends Mining
}

class Miner(accountKey: String, timeoutPoW: Long) extends Actor with ActorLogging {
  import Miner._

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = timeoutPoW.millis

  override def receive: Receive = idle

  def idle: Receive = {
    case Mine(blockPrev, trans) =>
      context.become(busy)

      val recipient = sender()

      val newTransItem = TransactionItem(
          ProofOfWork.networkAccount,
          new Account(accountKey, "miner"),
          ProofOfWork.defaultReward,
          System.currentTimeMillis
        )
      val newTrans = new Transactions(trans.id, newTransItem +: trans.items, System.currentTimeMillis)
      val newBlock = LinkedBlock(blockPrev, newTrans, System.currentTimeMillis)

      generatePoW(newBlock).map{ newNonce =>
          recipient ! newBlock.copy(nonce = newNonce)
        }.
        recover{ case e: Exception =>
          recipient ! Status.Failure(e)
        }

    case _ =>
      // Do nothing
  }

  def busy: Receive = {
    case Mine(b, t) =>
      log.error(s"[Mining] Miner.Mine($b, $t) received but $this is busy!")
      sender() ! Status.Failure(new Blockchainer.BusyException(s"$this is busy!"))

    case DoneMining =>
      context.become(idle)
      log.info(s"[Mining] Miner.DoneMining received.")
  }

  private def generatePoW(block: Block)(implicit ec: ExecutionContext, timeout: FiniteDuration): Future[Long] = {
    val promise = Promise[Long]()

    context.system.scheduler.scheduleOnce(timeout){ promise tryFailure new TimeoutException(s"$block: $timeout") }

    Future{
      Try{
        val incrementedNonce =
          ProofOfWork.generateProof(bytesToBase64(block.hash), defaultDifficulty, defaultNonce)
        promise success incrementedNonce
      }.
      recover{
        case e: Exception => promise failure e
      }
    }
    promise.future
  }
}
