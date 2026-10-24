package com.example.superdialer.calllog

/** How far back the statistics look. */
enum class StatsPeriod(val label: String, val days: Long?) {
    Week("지난 7일", 7),
    Month("지난 30일", 30),
    All("전체", null),
}

/** One person (one number) in the "most called" ranking. */
data class TopCaller(val number: String, val name: String?, val calls: Int, val talkSeconds: Long)

data class CallStats(
    val total: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    /** Rejected and blocked calls. */
    val declined: Int,
    /** Sum of the talk time of all calls. */
    val talkSeconds: Long,
    val top: List<TopCaller>,
)

object CallStatsCalculator {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val TOP_COUNT = 5

    /** The statistics of [entries] that happened within [period] before [nowMillis]. */
    fun calculate(entries: List<CallLogEntry>, period: StatsPeriod, nowMillis: Long): CallStats {
        val since = period.days?.let { nowMillis - it * DAY_MS }
        val inPeriod = if (since == null) entries else entries.filter { it.dateMillis >= since }

        // Private callers have no number; they cannot be ranked as a person.
        val top = inPeriod
            .filter { numberKey(it.number).isNotEmpty() }
            .groupBy { numberKey(it.number) }
            .map { (_, calls) ->
                val newest = calls.maxBy { it.dateMillis }
                TopCaller(
                    number = newest.number,
                    name = calls.firstNotNullOfOrNull { it.name },
                    calls = calls.size,
                    talkSeconds = calls.sumOf { it.durationSeconds },
                )
            }
            .sortedWith(compareByDescending<TopCaller> { it.calls }.thenByDescending { it.talkSeconds })
            .take(TOP_COUNT)

        return CallStats(
            total = inPeriod.size,
            incoming = inPeriod.count { it.type == CallType.Incoming },
            outgoing = inPeriod.count { it.type == CallType.Outgoing },
            missed = inPeriod.count { it.type == CallType.Missed },
            declined = inPeriod.count { it.type == CallType.Rejected || it.type == CallType.Blocked },
            talkSeconds = inPeriod.sumOf { it.durationSeconds },
            top = top,
        )
    }
}
