package akkablockchain.actor

import akkablockchain.model._

import akka.actor.{Actor, ActorLogging, Props, Status}

object BlockInspector {
  def props: Props = Props[BlockInspector]

  sealed trait Validation
  case class Validate(block: Block) extends Validation
  case object DoneValidation extends Validation
}

class BlockInspector extends Actor with ActorLogging {
  import BlockInspector._

  override def receive: Receive = idle

  def idle: Receive = {
    case Validate(block) =>
      context.become(busy)
      sender() ! validBlockchain(block)

    case _ =>
      // Do nothing
  }

  def busy: Receive = {
    case Validate(block) =>
      log.error(s"[Validation] BlockInspector.Validate($block) received but $this is busy!")
      sender() ! Status.Failure(new Blockchainer.BusyException(s"$this is busy!"))

    case DoneValidation =>
      context.become(idle)
      log.info(s"[Validation] BlockInspector.DoneValidation received.")
  }

  private def validBlockchain(block: Block): Boolean = {
    @scala.annotation.tailrec
    def loop(blk: Block): Boolean = blk match {
      case b: LinkedBlock =>
        if (ProofOfWork.validProofIn(b) &&
            b.hasValidHash &&
            b.hasValidMerkleRoot &&
            b.transactions.hasValidItems &&
            allNewTrans(block.transactions, b.blockPrev.transactions)
          )
          loop(b.blockPrev)
        else
          false  // Short-circuiting if `false`

      case b: RootBlock.type =>
        b.hasValidHash &&
          b.hasValidMerkleRoot
    }

    block match {
      case _: LinkedBlock => loop(block)
      case RootBlock => false
    }
  }

  private def allNewTrans(trans: Transactions, oldTrans: Transactions): Boolean =
    oldTrans.id != trans.id &&
      ! trans.items.exists(item => oldTrans.items.map(_.id).contains(item.id))
}
