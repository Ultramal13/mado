package app.mado.optics

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calibración propia del magnetómetro (sesgo «hard iron»: lo que suman al campo los imanes y el hierro que lleva el propio teléfono).
 *
 * Idea: con el teléfono en reposo, la gravedad da «arriba» en su propio sistema (u). El campo de la Tierra en este lugar tiene una
 * componente vertical conocida (fuerza e inclinación del modelo geomagnético) que **no depende de hacia dónde mira el teléfono**. Si el sensor
 * mide B = tierra + sesgo, entonces para cada muestra:  B·u − sesgo·u = vertical esperada,  es decir  sesgo·u = B·u − vertical.
 * Es lineal en el sesgo: con muestras en posiciones distintas (plano, de pie, de lado, boca abajo) se resuelve con mínimos cuadrados.
 */
class MagBiasEstimator(private val expectedVerticalUt: Double) {
    private val m = DoubleArray(9)          // Σ u uᵀ
    private val r = DoubleArray(3)          // Σ u (B·u − vertical)
    private var sumD2 = 0.0                 // Σ (B·u − vertical)²
    var n = 0
        private set

    /** Suma una muestra: [raw] campo medido (µT, sin quitarle sesgo) y [acc] aceleración (m/s², en reposo apunta hacia arriba). Devuelve si se usó. */
    fun add(raw: DoubleArray, acc: DoubleArray): Boolean {
        val an = sqrt(acc[0] * acc[0] + acc[1] * acc[1] + acc[2] * acc[2])
        if (an < 8.8 || an > 10.8) return false                          // el teléfono se está moviendo: la gravedad no se distingue del movimiento
        val u = doubleArrayOf(acc[0] / an, acc[1] / an, acc[2] / an)
        val d = raw[0] * u[0] + raw[1] * u[1] + raw[2] * u[2] - expectedVerticalUt
        for (i in 0..2) { r[i] += u[i] * d; for (j in 0..2) m[i * 3 + j] += u[i] * u[j] }
        sumD2 += d * d
        n++
        return true
    }

    /** 0..1: cuánto cubren las muestras las direcciones de «arriba» (hace falta variedad en los tres ejes) y cuántas hay. */
    fun coverage(): Double {
        if (n < 30) return n / 30.0 * 0.2
        val spread = minEigen(m.map { it / n }.toDoubleArray()) / FULL_SPREAD
        return (spread.coerceIn(0.0, 1.0) * 0.8 + minOf(1.0, n / 120.0) * 0.2).coerceIn(0.0, 1.0)
    }

    val ready get() = n >= 90 && minEigen(m.map { it / n }.toDoubleArray()) >= FULL_SPREAD

    /** Sesgo estimado (µT) en el sistema del teléfono, o null si faltan muestras variadas. */
    fun solve(): DoubleArray? {
        if (!ready) return null
        return solve3(m, r)
    }

    /** Error típico (µT) del ajuste: cuánto se aparta de la vertical esperada con el sesgo ya restado. */
    fun residualRms(bias: DoubleArray): Double {
        if (n == 0) return Double.NaN
        var bMb = 0.0; var br = 0.0
        for (i in 0..2) { br += bias[i] * r[i]; for (j in 0..2) bMb += bias[i] * m[i * 3 + j] * bias[j] }
        // Σ (d − b·u)² = Σd² − 2 b·r + bᵀ M b
        return sqrt(maxOf(0.0, (sumD2 - 2 * br + bMb) / n))
    }

    companion object {
        /** Mínimo del menor autovalor de Σuuᵀ/n: para direcciones repartidas por toda la esfera sería 1/3; con plano + de pie + de lado ya pasa de 0.08. */
        const val FULL_SPREAD = 0.08

        /** Menor autovalor de una matriz simétrica 3×3 (fórmula cerrada). */
        fun minEigen(a: DoubleArray): Double {
            val p1 = a[1] * a[1] + a[2] * a[2] + a[5] * a[5]
            val q = (a[0] + a[4] + a[8]) / 3.0
            if (p1 < 1e-15) return minOf(a[0], a[4], a[8])
            val p2 = (a[0] - q) * (a[0] - q) + (a[4] - q) * (a[4] - q) + (a[8] - q) * (a[8] - q) + 2 * p1
            val p = sqrt(p2 / 6.0)
            val b = DoubleArray(9) { (a[it] - (if (it % 4 == 0) q else 0.0)) / p }
            val detB = b[0] * (b[4] * b[8] - b[5] * b[7]) - b[1] * (b[3] * b[8] - b[5] * b[6]) + b[2] * (b[3] * b[7] - b[4] * b[6])
            val phi = acos((detB / 2.0).coerceIn(-1.0, 1.0)) / 3.0
            return q + 2 * p * cos(phi + 2.0 * Math.PI / 3.0)
        }

        /** Resuelve M x = r (3×3) por la regla de Cramer; null si es singular. */
        fun solve3(m: DoubleArray, r: DoubleArray): DoubleArray? {
            fun det(a: DoubleArray) = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
            val d = det(m)
            if (abs(d) < 1e-12) return null
            return DoubleArray(3) { c ->
                val t = m.copyOf(); for (row in 0..2) t[row * 3 + c] = r[row]
                det(t) / d
            }
        }
    }
}

object MagCalibration {
    /** Componente vertical (hacia arriba) del campo terrestre esperado, µT: con inclinación positiva hacia abajo, es −F·sen(I). */
    fun expectedVerticalUt(strengthUt: Double, inclinationDeg: Double) = -strengthUt * sin(Math.toRadians(inclinationDeg))

    /**
     * Corrige el rumbo [headingOsDeg] que da el sistema con el campo ya sin sesgo, expresado en el mismo «mundo» del sistema ([worldEast], [worldNorth]):
     * el norte magnético está donde apunta la parte horizontal de ese campo.
     */
    fun correctedHeading(headingOsDeg: Double, worldEast: Double, worldNorth: Double): Double {
        val delta = Math.toDegrees(atan2(worldEast, worldNorth))
        return ((headingOsDeg - delta) % 360.0 + 360.0) % 360.0
    }
}
