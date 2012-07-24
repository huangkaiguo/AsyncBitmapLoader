package com.android.util;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Huang Kaiguo (huangkaiguo.z@gmail.com)
 */
public abstract class AsyncBitmapLoader<T> {

    public interface LoadBitmapListener<T> {

        public void onBitmapLoading();

        public void onBitmapLoaded(T key, Bitmap value, boolean animate);
    }

    private static final int MAX_CACHE_SIZE = 8 * 1024 * 1024;

    private static final int MAX_THREAD_SIZE = 4;

    private final Map<T, InternalTask> mTasks;

    private final ExecutorService mThreadPool;

    private final LruCache<T, Bitmap> mCache;

    public AsyncBitmapLoader(int maxThreadSize, int maxCacheSize) {
        mTasks = new HashMap<T, InternalTask>();

        if (maxThreadSize > MAX_THREAD_SIZE) {
            maxThreadSize = MAX_THREAD_SIZE;
        }
        mThreadPool = Executors.newFixedThreadPool(maxThreadSize);

        if (maxCacheSize > MAX_CACHE_SIZE) {
            maxCacheSize = MAX_CACHE_SIZE;
        }
        mCache = new LruCache<T, Bitmap>(maxCacheSize) {
            @Override
            protected int sizeOf(final T key, final Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public void load(final T key, final LoadBitmapListener<T> listener) {
        if (mThreadPool.isShutdown()) {
            // Loader is closing now.
            return;
        }

        if (mTasks.containsKey(key)) {
            // Task with the given key is executing.
            return;
        }

        final Bitmap value = mCache.get(key);
        if (null != value) {
            listener.onBitmapLoading();
            listener.onBitmapLoaded(key, value, false);
            return;
        }

        new InternalTask(key, listener).executeOnExecutor(mThreadPool);
    }

    public void close() {
        mThreadPool.shutdown();
        for (final InternalTask task : mTasks.values()) {
            task.cancel(true);
        }
        mTasks.clear();
        mCache.evictAll();
    }

    abstract protected Bitmap createBitmap(T key);

    class InternalTask extends AsyncTask<String, String, Bitmap> {

        private final T mKey;

        private final LoadBitmapListener<T> mListener;

        public InternalTask(final T key, final LoadBitmapListener<T> listener) {
            mKey = key;
            mListener = listener;
        }

        @Override
        protected Bitmap doInBackground(final String... params) {
            return isCancelled() ? null : createBitmap(mKey);
        }

        @Override
        protected void onPreExecute() {
            mListener.onBitmapLoading();
            mTasks.put(mKey, this);
        }

        @Override
        protected void onPostExecute(final Bitmap result) {
            if (null != result) {
                mCache.put(mKey, result);
            }
            mListener.onBitmapLoaded(mKey, result, true);
            mTasks.remove(mKey);
        }

        @Override
        protected void onCancelled(final Bitmap result) {
            mTasks.remove(mKey);
        }
    }
}
