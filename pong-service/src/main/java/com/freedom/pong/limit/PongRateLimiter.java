package com.freedom.pong.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class PongRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PongRateLimiter.class);
    private static final String LOCK_FILE = "/data/pong_rate_limit.lock";
    private final Integer MAX_REQUESTS_PER_SECOND;
    private final ReentrantLock threadLock = new ReentrantLock(); // 线程锁

    public PongRateLimiter(PongProperties pongProperties) {
        this.MAX_REQUESTS_PER_SECOND = pongProperties.getLimitQps();
    }

    public boolean tryAcquire() {
        long currentTime = System.currentTimeMillis() / 1000; // Current second timestamp

        threadLock.lock();
        try (RandomAccessFile stateFile = new RandomAccessFile(LOCK_FILE, "rw");
             FileChannel channel = stateFile.getChannel()) {

            // Attempt to acquire the file lock
            FileLock lock = channel.lock();
            if (lock == null) {
                log.info("Could not acquire lock, another process is holding it.");
                return false;
            }

            try {
                // Read state: timestamp and counter
                long lastTimeStamp = 0;
                int requestCount = 0;

                if (stateFile.length() > 0) {
                    stateFile.seek(0); // Move file pointer to the start
                    lastTimeStamp = stateFile.readLong(); // Read last timestamp
                    requestCount = stateFile.readInt();   // Read last request count
                }

                // Check if within the same second
                if (currentTime == lastTimeStamp) {
                    if (requestCount >= MAX_REQUESTS_PER_SECOND) {
                        log.info("Rate limit reached for second: {}", currentTime);
                        return false; // Rate limit reached
                    } else {
                        requestCount++; // Increment request count
                    }
                } else {
                    // New second, reset counter
                    lastTimeStamp = currentTime;
                    requestCount = 1;
                }

                // Write new state back to file
                stateFile.seek(0); // Move pointer to the start
                stateFile.writeLong(lastTimeStamp); // Write new timestamp
                stateFile.writeInt(requestCount);   // Write new count

                log.info("Request allowed for second: {}, count: {}", currentTime, requestCount);
                return true; // Allow request

            } finally {
                lock.release(); // Ensure the file lock is released
            }
        } catch (IOException e) {
            log.error("Error accessing rate limit file", e);
            return false; // Deny request on error
        } finally {
            threadLock.unlock(); // Release the intra-process lock
        }
    }
}