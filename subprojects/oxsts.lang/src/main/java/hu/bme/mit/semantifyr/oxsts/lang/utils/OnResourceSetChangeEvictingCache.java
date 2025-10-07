/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.util.*;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cache implementation that stores its values in the scope of a resource set.
 * The values will be discarded as soon as the contents of the resource set changes.
 */
@Singleton
public class OnResourceSetChangeEvictingCache implements IResourceScopeCache {

    private static final Logger log = Logger.getLogger(OnResourceSetChangeEvictingCache.class);

    @Override
    public void clear(Resource resource) {
        var cacheAdapter = findCacheAdapter(resource.getResourceSet());
        if (cacheAdapter != null) {
            cacheAdapter.clearValues();
        }
    }

    @Override
    public <T> T get(Object key, Resource resource, Provider<T> provider) {
        if(resource == null) {
            return provider.get();
        }
        var adapter = getOrCreate(resource.getResourceSet());
        T element = adapter.internalGet(resource, key);
        if (element == null) {
            element = provider.get();
            cacheMiss(adapter);
            adapter.set(resource, key, element);
        } else {
            cacheHit(adapter);
        }
        if (element == CacheAdapter.EMPTY_VALUE) {
            return null;
        }
        return element;
    }

    protected void cacheMiss(CacheAdapter adapter) {
        adapter.cacheMiss();
    }

    protected void cacheHit(CacheAdapter adapter) {
        adapter.cacheHit();
    }

    public CacheAdapter getOrCreate(ResourceSet resourceSet) {
        CacheAdapter adapter = findCacheAdapter(resourceSet);
        if (adapter == null) {
            adapter = createCacheAdapter();
            resourceSet.eAdapters().add(adapter);
            adapter.setResourceSet(resourceSet);
        }
        return adapter;
    }

    protected CacheAdapter findCacheAdapter(ResourceSet resourceSet) {
        return (CacheAdapter) EcoreUtil.getAdapter(resourceSet.eAdapters(), CacheAdapter.class);
    }

    protected CacheAdapter createCacheAdapter() {
        return new CacheAdapter();
    }

    /**
     * The transaction will be executed. While it is running, any semantic state change
     * in the given resource will be ignored and the cache will not be cleared.
     */
    public <Result, Param extends Resource> Result execWithoutCacheClear(Param resource, IUnitOfWork<Result, Param> transaction) throws WrappedException {
        var cacheAdapter = getOrCreate(resource.getResourceSet());
        try {
            cacheAdapter.ignoreNotifications();
            return transaction.exec(resource);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        } finally {
            cacheAdapter.listenToNotifications();
        }
    }

    public static class CacheAdapter extends NonRecursiveEContentAdapter {

        private static final Object EMPTY_VALUE = new Object();

        private final Map<Pair<Resource, Object>, Object> values;

        private final AtomicInteger ignoreNotificationCounter = new AtomicInteger(0);

        private volatile boolean empty = true;

        private ResourceSet resourceSet;

        private int misses = 0;
        private int hits = 0;

        public CacheAdapter() {
            this(500);
        }

        public CacheAdapter(int initialCapacity) {
            values = new ConcurrentHashMap<>(initialCapacity);
        }

        protected Pair<Resource, Object> getRealKey(Resource resource, Object key) {
            return Tuples.create(resource, key);
        }

        public void set(Resource resource, Object key, Object value) {
            empty = false;
            values.put(getRealKey(resource, key), Objects.requireNonNullElse(value, EMPTY_VALUE));
        }

        protected void cacheMiss() {
            misses++;
        }

        protected void cacheHit() {
            hits++;
        }

        public void listenToNotifications() {
            if (ignoreNotificationCounter.decrementAndGet() < 0) {
                throw new IllegalStateException("ignoreNotificationCounter may not be less than zero");
            }
        }

        public void ignoreNotifications() {
            ignoreNotificationCounter.incrementAndGet();
        }

        private <T> T internalGet(Resource resource, Object key) {
            if (empty) {
                return null;
            }
            //noinspection unchecked
            return (T) this.values.get(getRealKey(resource, key));
        }

        public <T> T get(Resource resource, Object key) {
            T result = internalGet(resource, key);
            if (result != EMPTY_VALUE) {
                return result;
            }
            return null;
        }

        @Override
        public void notifyChanged(Notification notification) {
            super.notifyChanged(notification);
            if (ignoreNotificationCounter.get() == 0 && isSemanticStateChange(notification)) {
                clearValues();
            }
        }

        public void clearValues() {
            if (!empty) {
                values.clear();
                empty = true;
                misses = 0;
                hits = 0;
            }
        }

        protected boolean isSemanticStateChange(Notification notification) {
            if (notification.isTouch()) {
                return false;
            }
            if (notification.getNotifier() instanceof Resource) {
                switch(notification.getFeatureID(Resource.class)) {
                    case Resource.RESOURCE__IS_MODIFIED:
                    case Resource.RESOURCE__IS_TRACKING_MODIFICATION:
                    case Resource.RESOURCE__TIME_STAMP:
                    case Resource.RESOURCE__ERRORS:
                    case Resource.RESOURCE__WARNINGS:
                        return false;
                }
            }
            return true;
        }

        @Override
        public boolean isAdapterForType(Object type) {
            return type == getClass() || type == OnResourceSetChangeEvictingCache.class || type == CacheAdapter.class;
        }

        public boolean isIgnoreNotifications() {
            return ignoreNotificationCounter.get() > 0;
        }

        @Override
        protected boolean resolve() {
            return false;
        }

        public ResourceSet getResourceSet() {
            return resourceSet;
        }

        public void setResourceSet(ResourceSet resourceSet) {
            this.resourceSet = resourceSet;
        }
    }

}
