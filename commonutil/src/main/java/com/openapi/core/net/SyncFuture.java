package com.openapi.core.net;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * 阻塞等待一次请求结果。取代 Volley 的 RequestFuture：调用方在后台线程里 [get]，
 * 请求在线程池里执行，结果或错误通过回调交给这里。
 */
public class SyncFuture<T> implements Response.Listener<T>, Response.ErrorListener {
    private final CountDownLatch latch = new CountDownLatch(1);
    private T result;
    private NetError error;

    public static <T> SyncFuture<T> newFuture() {
        return new SyncFuture<>();
    }

    @Override
    public synchronized void onResponse(T response) {
        if (latch.getCount() == 0) return;
        result = response;
        latch.countDown();
    }

    @Override
    public synchronized void onErrorResponse(NetError error) {
        if (latch.getCount() == 0) return;
        this.error = error;
        latch.countDown();
    }

    /** 阻塞直到结果到达；失败时抛出 [ExecutionException]，原因是 [NetError]。 */
    public T get() throws InterruptedException, ExecutionException {
        latch.await();
        synchronized (this) {
            if (error != null) {
                throw new ExecutionException(error);
            }
            return result;
        }
    }
}
