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
public class GlobalRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(GlobalRateLimiter.class);
    private static final String LOCK_FILE = "/data/ping_rate_limit.lock";

    private static Integer MAX_REQUESTS_PER_SECOND = 2;

    private final ReentrantLock threadLock = new ReentrantLock(); // 线程锁


    /*private static final int MAX_REQUESTS = 2;

    public GlobalRateLimiter(PingProperties pingProperties) {
        this.MAX_REQUESTS_PER_SECOND = pingProperties.getLimitQps();
    }*/

    public boolean tryAcquire() throws IOException {
        long currentTime = System.currentTimeMillis() / 1000; // 当前秒级时间戳

        threadLock.lock();
        try (RandomAccessFile stateFile = new RandomAccessFile(LOCK_FILE, "rw");
             FileChannel channel = stateFile.getChannel()) {

            FileLock lock = channel.lock();
            if (lock == null) {
                log.info("Could not acquire lock, another process is holding it.");
                return false;
            }

            try {

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
                lock.release();
            }
        } catch (IOException e) {
            log.error("Error accessing rate limit file", e);
            return false;
        } finally {
            threadLock.unlock();
        }
    }
}
