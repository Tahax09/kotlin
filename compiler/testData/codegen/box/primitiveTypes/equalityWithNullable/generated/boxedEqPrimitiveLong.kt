// Auto-generated by GeneratePrimitiveVsObjectEqualityTestData. Do not edit!

val nx: Long? = 0L
val nn: Long? = null
val x: Long = 0L
val y: Long = 1L

fun box(): String {
    val ax: Long? = 0L
    val an: Long? = null
    val bx: Long = 0L
    val by: Long = 1L

    return when {
        nx != 0L -> "Fail 0"
        nx == 1L -> "Fail 1"
        !(nx == 0L) -> "Fail 2"
        !(nx != 1L) -> "Fail 3"
        nx != x -> "Fail 4"
        nx == y -> "Fail 5"
        !(nx == x) -> "Fail 6"
        !(nx != y) -> "Fail 7"
        nn == 0L -> "Fail 8"
        !(nn != 0L) -> "Fail 9"
        nn == x -> "Fail 10"
        !(nn != x) -> "Fail 11"
        ax != 0L -> "Fail 12"
        ax == 1L -> "Fail 13"
        !(ax == 0L) -> "Fail 14"
        !(ax != 1L) -> "Fail 15"
        ax != bx -> "Fail 16"
        ax == by -> "Fail 17"
        !(ax == bx) -> "Fail 18"
        !(ax != by) -> "Fail 19"
        an == 0L -> "Fail 20"
        !(an != 0L) -> "Fail 21"
        an == bx -> "Fail 22"
        !(an != bx) -> "Fail 23"
        else -> "OK"
    }
}
