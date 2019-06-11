package ingidoexamples.model

import indigo.shared.temporal.Signal
import indigo.shared.temporal.SignalFunction
import indigo.shared.time.Millis
import indigoexts.subsystems.automata.AutomatonPayload

final class TrailParticle(val fallen: Double, val alpha: Double) extends AutomatonPayload {

  override def toString: String =
    s"TrailParticle(fallen = ${fallen.toString}, alpha = ${alpha.toString})"

}

object TrailParticle {

  def apply(fallen: Double, alpha: Double): TrailParticle =
    new TrailParticle(fallen, alpha)

  def unapply(trailParticle: TrailParticle): Option[(Double, Double)] =
    Some((trailParticle.fallen, trailParticle.alpha))

  def create: TrailParticle =
    TrailParticle(0.0d, 1.0d)

  def fall(lifeSpan: Millis): SignalFunction[Millis, Double] =
    SignalFunction { t =>
      (t.toDouble / 1000) * ((t.toDouble / lifeSpan.toDouble) * 2)
    }

  def fade(lifeSpan: Millis): SignalFunction[Millis, Double] =
    SignalFunction { t =>
      1 - (t.toDouble / lifeSpan.toDouble)
    }

  val combine: SignalFunction[(Double, Double), TrailParticle] =
    SignalFunction { case (p, a) => TrailParticle(p, a) }

  def particle(lifeSpan: Millis): Signal[TrailParticle] =
    Signal.clampTime(Signal.Time, Millis.zero, lifeSpan) |>
      ((fall(lifeSpan) &&& fade(lifeSpan)) >>> combine)

}