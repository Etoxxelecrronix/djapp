package com.djapp.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cooley-Tukey radix-2 in-place FFT.
 * Both arrays must have the same length, which must be a power of 2.
 */
fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    require(im.size == n) { "Real and imaginary arrays must have the same length" }
    require(n > 0 && (n and (n - 1)) == 0) { "Length must be a power of 2" }

    // Bit-reversal permutation
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            var temp = re[i]; re[i] = re[j]; re[j] = temp
            temp = im[i]; im[i] = im[j]; im[j] = temp
        }
    }

    // Butterfly operations
    var len = 2
    while (len <= n) {
        val halfLen = len / 2
        val angle = -2.0 * PI / len
        val wRe = cos(angle)
        val wIm = sin(angle)

        var i = 0
        while (i < n) {
            var curRe = 1.0
            var curIm = 0.0

            for (k in 0 until halfLen) {
                val tRe = curRe * re[i + k + halfLen] - curIm * im[i + k + halfLen]
                val tIm = curRe * im[i + k + halfLen] + curIm * re[i + k + halfLen]

                re[i + k + halfLen] = re[i + k] - tRe
                im[i + k + halfLen] = im[i + k] - tIm
                re[i + k] = re[i + k] + tRe
                im[i + k] = im[i + k] + tIm

                val newCurRe = curRe * wRe - curIm * wIm
                curIm = curRe * wIm + curIm * wRe
                curRe = newCurRe
            }
            i += len
        }
        len = len shl 1
    }
}
