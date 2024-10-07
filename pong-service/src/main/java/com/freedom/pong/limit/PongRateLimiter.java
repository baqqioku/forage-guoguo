package com.freedom.pong.limit;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

@Component
public class PongRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PongRateLimiter.class);
    private static final String LOCK_FILE = "pong_rate_limit.lock";
    //private  RateLimiter rateLimiter ;

    private  Double MAX_REQUESTS_PER_SECOND = Double.MAX_VALUE;
    private long lastTimeStamp = 0;
    private int requestCount = 0;

    public PongRateLimiter(PongProperties pongProperties){
        this.MAX_REQUESTS_PER_SECOND = pongProperties.getLimitQps();
    }

   /* public boolean tryAcquire() {
        try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                try {
                    return rateLimiter.tryAcquire();
                } finally {
                    lock.release();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("limit error:{}",e.getMessage());
        }
        return false;
    }*/

    public boolean canSendRequest() throws IOException {
        long currentTime = System.currentTimeMillis() / 1000; // Current second

        try (FileLock lock = acquireFileLock()) {
            // FileLock ensures synchronization across processes
            synchronized (this) {
                if (currentTime == lastTimeStamp) {
                    if (requestCount >= MAX_REQUESTS_PER_SECOND) {
                        return false; // Rate limit hit
                    } else {
                        requestCount++; // Increment request count
                    }
                } else {
                    // New second, reset request count
                    lastTimeStamp = currentTime;
                    requestCount = 1;
                }
            }
        }
        return true; // Allow request
    }

    private FileLock acquireFileLock() throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(LOCK_FILE, "rw");
        FileChannel channel = randomAccessFile.getChannel();
        return channel.lock(); // Acquire the lock to ensure only one process is modifying the request count
    }
}
