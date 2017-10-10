package de.twentyone

object ProcessUtil {
  implicit val stringToProcess = _root_.sbt.Process.stringToProcess _
  def ReProcess(seq: Seq[String]) = _root_.sbt.Process(seq)
}
