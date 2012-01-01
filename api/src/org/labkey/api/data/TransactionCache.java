/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.labkey.api.cache.BasicCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheType;
import org.labkey.api.cache.Stats;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.util.Filter;

/*
* User: adam
* Date: Nov 9, 2009
* Time: 10:49:04 PM
*/
public class TransactionCache<V> implements StringKeyCache<V>
{
    private static final Object _nullMarker = BasicCache.NULL_MARKER;
    private boolean _hasWritten = false;
    private final StringKeyCache<V> _sharedCache;
    private final StringKeyCache<Object> _privateCache;

    // A read-through transaction cache.  Reads through to the passed-in shared cache until any write occurs, at which
    // point it switches to using a private cache for the remainder of the transaction.
    public TransactionCache(StringKeyCache<V> sharedCache)
    {
        _privateCache = CacheManager.getTemporaryCache(sharedCache.getLimit(), sharedCache.getDefaultExpires(), "transaction cache: " + sharedCache.getDebugName(), sharedCache.getTransactionStats());
        _sharedCache = sharedCache;
    }

    @Override
    public V get(String key)
    {
        Object v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        assert v != _nullMarker; // TODO: remove this check

        return v == _nullMarker ? null : (V)v;
    }


    @Override
    public V get(String key, Object arg, CacheLoader<String, V> loader)
    {
        Object v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        if (null == v)
        {
            v = loader.load(key, arg);
            _hasWritten = true;
            _privateCache.put(key, v == null ? _nullMarker : (V)v);
        }

        return v == _nullMarker ? null : (V)v;
    }

    @Override
    public void put(String key, V value)
    {
        _hasWritten = true;
        _privateCache.put(key, value);
    }

    @Override
    public void put(String key, V value, long timeToLive)
    {
        _hasWritten = true;
        _privateCache.put(key, value, timeToLive);
    }

    @Override
    public void remove(String key)
    {
        _hasWritten = true;
        _privateCache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<String> filter)
    {
        _hasWritten = true;
        return _privateCache.removeUsingFilter(filter);
    }

    @Override
    public void clear()
    {
        _hasWritten = true;
        _privateCache.clear();
    }

    @Override
    public int removeUsingPrefix(String prefix)
    {
        _hasWritten = true;
        return _privateCache.removeUsingPrefix(prefix);
    }

    @Override
    public int getLimit()
    {
        return _privateCache.getLimit();
    }

    @Override
    public CacheType getCacheType()
    {
        return _sharedCache.getCacheType();
    }

    @Override
    public void close()
    {
        _privateCache.close();
    }

    @Override
    public Stats getStats()
    {
        return _privateCache.getStats();
    }

    @Override
    public int size()
    {
        return _privateCache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _privateCache.getDefaultExpires();
    }

    @Override
    public String getDebugName()
    {
        return _privateCache.getDebugName();
    }

    @Override
    public StackTraceElement[] getCreationStackTrace()
    {
        return _privateCache.getCreationStackTrace();
    }

    @Override
    public Stats getTransactionStats()
    {
        return null; // TODO: Check this
    }
}
