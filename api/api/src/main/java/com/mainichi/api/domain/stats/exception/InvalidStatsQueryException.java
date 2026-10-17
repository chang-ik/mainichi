package com.mainichi.api.domain.stats.exception;

/** deck 이 all/words/grammar 가 아니거나 days 가 30/90/365 가 아닌 경우 */
public class InvalidStatsQueryException extends RuntimeException {
    public InvalidStatsQueryException() {
        super("INVALID_STATS_QUERY");
    }
}
