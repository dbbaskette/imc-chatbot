package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

/**
 * Manages the state of MCP connections using a state machine approach.
 * Provides better connection lifecycle management and state transitions.
 */
@Service
@Profile("mcp")
public class McpConnectionStateManager {
    
    private static final Logger log = LoggerFactory.getLogger(McpConnectionStateManager.class);
    
    /**
     * Connection states for the MCP connection lifecycle
     */
    public enum ConnectionState {
        DISCONNECTED("Disconnected"),
        CONNECTING("Connecting"),
        CONNECTED("Connected"),
        DEGRADED("Degraded"),
        RECONNECTING("Reconnecting");
        
        private final String displayName;
        
        ConnectionState(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    // State tracking
    private final AtomicReference<ConnectionState> currentState = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    
    // Timing tracking
    private final AtomicReference<LocalDateTime> lastConnectionTime = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastDisconnectionTime = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastStateChangeTime = new AtomicReference<>(LocalDateTime.now());
    
    // Performance tracking
    private final AtomicReference<Duration> lastConnectionDuration = new AtomicReference<>();
    private final AtomicReference<Duration> averageConnectionDuration = new AtomicReference<>(Duration.ZERO);
    
    public McpConnectionStateManager() {
        log.info("🔄 MCP Connection State Manager initialized");
    }
    
    /**
     * Gets the current connection state
     */
    public ConnectionState getCurrentState() {
        return currentState.get();
    }
    
    /**
     * Transitions to a new connection state
     */
    public void transitionTo(ConnectionState newState) {
        ConnectionState oldState = currentState.getAndSet(newState);
        lastStateChangeTime.set(LocalDateTime.now());
        
        if (oldState != newState) {
            log.info("🔄 MCP Connection state: {} → {}", oldState, newState);
            
            // Track state-specific metrics
            switch (newState) {
                case CONNECTED -> onConnected();
                case DISCONNECTED -> onDisconnected();
                case RECONNECTING -> onReconnecting();
                case DEGRADED -> onDegraded();
                case CONNECTING -> onConnecting();
            }
        }
    }
    
    /**
     * Attempts to connect and updates state accordingly
     */
    public void attemptConnection() {
        connectionAttempts.incrementAndGet();
        transitionTo(ConnectionState.CONNECTING);
        log.debug("🔌 Attempting MCP connection (attempt #{})", connectionAttempts.get());
    }
    
    /**
     * Called when a connection is successfully established
     */
    public void onConnectionSuccess() {
        successfulConnections.incrementAndGet();
        connectionAttempts.set(0);
        lastConnectionTime.set(LocalDateTime.now());
        
        // Calculate connection duration if we were connecting
        if (currentState.get() == ConnectionState.CONNECTING) {
            LocalDateTime lastChange = lastStateChangeTime.get();
            if (lastChange != null) {
                Duration connectionDuration = Duration.between(lastChange, LocalDateTime.now());
                lastConnectionDuration.set(connectionDuration);
                updateAverageConnectionDuration(connectionDuration);
                log.debug("⏱️ Connection established in {}ms", connectionDuration.toMillis());
            }
        }
        
        transitionTo(ConnectionState.CONNECTED);
    }
    
    /**
     * Called when a connection attempt fails
     */
    public void onConnectionFailure(String reason) {
        failedConnections.incrementAndGet();
        log.warn("❌ MCP connection failed: {}", reason);
        
        if (currentState.get() == ConnectionState.CONNECTING) {
            // If we were trying to connect, go back to disconnected
            transitionTo(ConnectionState.DISCONNECTED);
        } else if (currentState.get() == ConnectionState.CONNECTED) {
            // If we were connected, mark as degraded
            transitionTo(ConnectionState.DEGRADED);
        }
    }
    
    /**
     * Called when a connection is lost
     */
    public void onConnectionLost(String reason) {
        log.warn("🔌 MCP connection lost: {}", reason);
        lastDisconnectionTime.set(LocalDateTime.now());
        transitionTo(ConnectionState.DISCONNECTED);
    }
    
    /**
     * Initiates a reconnection attempt
     */
    public void initiateReconnection() {
        log.info("🔄 Initiating MCP reconnection...");
        transitionTo(ConnectionState.RECONNECTING);
        attemptConnection();
    }
    
    /**
     * Checks if the connection is healthy
     */
    public boolean isConnectionHealthy() {
        ConnectionState state = currentState.get();
        return state == ConnectionState.CONNECTED || state == ConnectionState.DEGRADED;
    }
    
    /**
     * Checks if the connection is actively being established
     */
    public boolean isConnecting() {
        return currentState.get() == ConnectionState.CONNECTING || 
               currentState.get() == ConnectionState.RECONNECTING;
    }
    
    /**
     * Gets connection statistics
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
            currentState.get(),
            connectionAttempts.get(),
            successfulConnections.get(),
            failedConnections.get(),
            lastConnectionTime.get(),
            lastDisconnectionTime.get(),
            lastStateChangeTime.get(),
            lastConnectionDuration.get(),
            averageConnectionDuration.get(),
            calculateSuccessRate()
        );
    }
    
    /**
     * Resets all connection statistics
     */
    public void resetStats() {
        connectionAttempts.set(0);
        successfulConnections.set(0);
        failedConnections.set(0);
        lastConnectionTime.set(null);
        lastDisconnectionTime.set(null);
        lastStateChangeTime.set(LocalDateTime.now());
        lastConnectionDuration.set(null);
        averageConnectionDuration.set(Duration.ZERO);
        log.info("🔄 Connection statistics reset");
    }
    
    // Private helper methods for state transitions
    private void onConnected() {
        log.info("✅ MCP connection established successfully");
    }
    
    private void onDisconnected() {
        log.info("🔌 MCP connection disconnected");
    }
    
    private void onReconnecting() {
        log.info("🔄 MCP connection reconnecting...");
    }
    
    private void onDegraded() {
        log.warn("⚠️ MCP connection degraded - some functionality may be limited");
    }
    
    private void onConnecting() {
        log.debug("🔌 MCP connection attempt in progress...");
    }
    
    private void updateAverageConnectionDuration(Duration newDuration) {
        Duration currentAvg = averageConnectionDuration.get();
        if (currentAvg.equals(Duration.ZERO)) {
            averageConnectionDuration.set(newDuration);
        } else {
            // Simple moving average
            long newAvgMillis = (currentAvg.toMillis() + newDuration.toMillis()) / 2;
            averageConnectionDuration.set(Duration.ofMillis(newAvgMillis));
        }
    }
    
    private double calculateSuccessRate() {
        int total = successfulConnections.get() + failedConnections.get();
        return total > 0 ? (double) successfulConnections.get() / total * 100 : 0.0;
    }
    
    /**
     * Data class for connection statistics
     */
    public static class ConnectionStats {
        private final ConnectionState currentState;
        private final int connectionAttempts;
        private final int successfulConnections;
        private final int failedConnections;
        private final LocalDateTime lastConnectionTime;
        private final LocalDateTime lastDisconnectionTime;
        private final LocalDateTime lastStateChangeTime;
        private final Duration lastConnectionDuration;
        private final Duration averageConnectionDuration;
        private final double successRate;
        
        public ConnectionStats(ConnectionState currentState, int connectionAttempts,
                            int successfulConnections, int failedConnections,
                            LocalDateTime lastConnectionTime, LocalDateTime lastDisconnectionTime,
                            LocalDateTime lastStateChangeTime, Duration lastConnectionDuration,
                            Duration averageConnectionDuration, double successRate) {
            this.currentState = currentState;
            this.connectionAttempts = connectionAttempts;
            this.successfulConnections = successfulConnections;
            this.failedConnections = failedConnections;
            this.lastConnectionTime = lastConnectionTime;
            this.lastDisconnectionTime = lastDisconnectionTime;
            this.lastStateChangeTime = lastStateChangeTime;
            this.lastConnectionDuration = lastConnectionDuration;
            this.averageConnectionDuration = averageConnectionDuration;
            this.successRate = successRate;
        }
        
        // Getters
        public ConnectionState getCurrentState() { return currentState; }
        public int getConnectionAttempts() { return connectionAttempts; }
        public int getSuccessfulConnections() { return successfulConnections; }
        public int getFailedConnections() { return failedConnections; }
        public LocalDateTime getLastConnectionTime() { return lastConnectionTime; }
        public LocalDateTime getLastDisconnectionTime() { return lastDisconnectionTime; }
        public LocalDateTime getLastStateChangeTime() { return lastStateChangeTime; }
        public Duration getLastConnectionDuration() { return lastConnectionDuration; }
        public Duration getAverageConnectionDuration() { return averageConnectionDuration; }
        public double getSuccessRate() { return successRate; }
        
        /**
         * Gets the time since the last state change
         */
        public Duration getTimeSinceLastStateChange() {
            if (lastStateChangeTime == null) {
                return Duration.ZERO;
            }
            return Duration.between(lastStateChangeTime, LocalDateTime.now());
        }
        
        /**
         * Gets the time since the last successful connection
         */
        public Duration getTimeSinceLastConnection() {
            if (lastConnectionTime == null) {
                return Duration.ZERO;
            }
            return Duration.between(lastConnectionTime, LocalDateTime.now());
        }
    }
}
