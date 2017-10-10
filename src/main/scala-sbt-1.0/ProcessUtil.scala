package de.twentyone

object ProcessUtil {
  implicit val stringToProcess =scala.sys.process.stringToProcess _

  def ReProcess(seq: Seq[String]) = scala.sys.process.Process(seq)
}
