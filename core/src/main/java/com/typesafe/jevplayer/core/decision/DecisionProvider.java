package com.typesafe.jevplayer.core.decision;

public interface DecisionProvider extends AutoCloseable {
    String getName();

    DecisionResult decide(DecisionRequest request) throws Exception;

    void warmUp();

    double getTotalSpendUsd();

    boolean isSpendCapReached();

    long getRollingP50LatencyMs();

    long getRollingP95LatencyMs();

    double getErrorRate();

    @Override
    default void close() {}
}
