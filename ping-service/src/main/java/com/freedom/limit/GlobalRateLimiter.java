package com.freedom.limit;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

@Component
public class GlobalRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(GlobalRateLimiter.class);
    private static final String LOCK_FILE = "/data/ping_rate_limit.lock";

    private Integer MAX_REQUESTS_PER_SECOND = Integer.MAX_VALUE;

    public GlobalRateLimiter(PingProperties pingProperties) {
        this.MAX_REQUESTS_PER_SECOND = pingProperties.getLimitQps();
    }

    public boolean tryAcquire() throws IOException{
        long currentTime = System.currentTimeMillis() / 1000; // 当前秒级时间戳

        FileLock lock = null;
        RandomAccessFile stateFile = null;
        try {
            // 打开状态文件并获取文件锁
            stateFile = new RandomAccessFile(LOCK_FILE, "rw");
            FileChannel channel = stateFile.getChannel();
            lock = channel.lock(); // 锁定整个文件

            // 读取状态：时间戳和计数器
            long lastTimeStamp = 0;
            int requestCount = 0;

            if (stateFile.length() > 0) {
                stateFile.seek(0); // 将文件指针移动到文件开头
                lastTimeStamp = stateFile.readLong(); // 读取上次的时间戳
                requestCount = stateFile.readInt();   // 读取上次的请求计数
            }

            // 判断是否在同一秒内，如果是则检查计数
            if (currentTime == lastTimeStamp) {
                if (requestCount >= MAX_REQUESTS_PER_SECOND) {
                    return false; // 达到限流
                } else {
                    requestCount++; // 增加请求计数
                }
            } else {
                // 如果是新的一秒，重置计数器
                lastTimeStamp = currentTime;
                requestCount = 1;
            }

            // 将新状态写回文件
            stateFile.seek(0); // 移动指针到文件头
            stateFile.writeLong(lastTimeStamp); // 写入新的时间戳
            stateFile.writeInt(requestCount);   // 写入新的计数

        } finally {
            // 释放锁并关闭文件
            if (lock != null) {
                lock.release();
            }
            if (stateFile != null) {
                stateFile.close();
            }
        }
        return true; // 允许请求
    }
}
