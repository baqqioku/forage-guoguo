package com.freedom.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class PingRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PingRateLimiter.class);
    private static final String LOCK_FILE = "/data/ping_rate_limit.lock";

    private static Integer MAX_REQUESTS_PER_SECOND = 2;

    private final ReentrantLock threadLock = new ReentrantLock(); // 线程锁

    public PingRateLimiter(PingProperties pingProperties) {
        this.MAX_REQUESTS_PER_SECOND = pingProperties.getLimitQps();
    }

    public boolean tryAcquire() {
        long currentTime = System.currentTimeMillis() / 1000;

        threadLock.lock();
        try {
            boolean result = doTryAcquire(currentTime);
            log.info("Rate limiter acquire result: {}", result);
            return result;
        } finally {
            threadLock.unlock();
        }
    }

    private boolean doTryAcquire(long currentTime) {
        int retries = 3;
        while (retries > 0) {
            try (RandomAccessFile stateFile = new RandomAccessFile(LOCK_FILE, "rw");
                 FileChannel channel = stateFile.getChannel()) {

                FileLock lock = null;
                try {
                    lock = channel.tryLock();
                    if (lock == null) {
                        log.info("Could not acquire lock, another process is holding it.");
                        return false;
                    }

                    long lastTimeStamp = 0;
                    int requestCount = 0;

                    if (stateFile.length() > 0) {
                        stateFile.seek(0);
                        lastTimeStamp = stateFile.readLong();
                        requestCount = stateFile.readInt();
                    }

                    if (currentTime == lastTimeStamp) {
                        if (requestCount >= MAX_REQUESTS_PER_SECOND) {
                            log.info("Rate limit reached for second: {}", currentTime);
                            return false;
                        } else {
                            requestCount++;
                        }
                    } else {
                        lastTimeStamp = currentTime;
                        requestCount = 1;
                    }

                    stateFile.seek(0);
                    stateFile.writeLong(lastTimeStamp);
                    stateFile.writeInt(requestCount);

                    log.info("Request allowed for second: {}, count: {}", currentTime, requestCount);
                    return true;

                } finally {
                    if (lock != null) {
                        lock.release();
                    }
                }
            } catch (IOException e) {
                log.error("Error accessing rate limit file", e);
                retries--;
                if (retries == 0) {
                    return false;
                }
                try {
                    Thread.sleep(10); // 短暂等待后重试
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
