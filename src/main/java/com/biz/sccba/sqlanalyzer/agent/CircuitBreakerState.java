package com.biz.sccba.sqlanalyzer.agent;

/**
 * Simple circuit breaker state for database operations.
 */
public class CircuitBreakerState {
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }
    
    private State currentState = State.CLOSED;
    private int failureCount = 0;
    private long lastFailureTime = 0;
    private final int failureThreshold;
    private final long timeoutMs;
    
    public CircuitBreakerState(int failureThreshold, long timeoutMs) {
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
    }
    
    public synchronized boolean allowRequest() {
        if (currentState == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > timeoutMs) {
                currentState = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }
    
    public synchronized void recordSuccess() {
        failureCount = 0;
        currentState = State.CLOSED;
    }
    
    public synchronized void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        if (failureCount >= failureThreshold) {
            currentState = State.OPEN;
        }
    }
    
    public State getState() {
        return currentState;
    }
}