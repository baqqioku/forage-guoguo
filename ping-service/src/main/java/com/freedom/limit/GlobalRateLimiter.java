package com.freedom.limit;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

@Component
public class GlobalRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(GlobalRateLimiter.class);
    private final File lockFile = new File("rate_limiter.lock");
    private  RateLimiter rateLimiter ;

    public GlobalRateLimiter(PingProperties pingProperties){
        this.rateLimiter = RateLimiter.create(pingProperties.getLimitQps());
    }

    public boolean tryAcquire() {
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
    }
}
