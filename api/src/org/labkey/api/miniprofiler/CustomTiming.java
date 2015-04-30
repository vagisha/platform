/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.miniprofiler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.ExceptionUtil;

/**
 * User: kevink
 */
@JsonPropertyOrder({"message", "duration", "stackTrace", "detailsUrl"})
public class CustomTiming implements AutoCloseable
{
    /*package*/ final Timing _parent;
    private final String _category;
    private final String _message; // TODO: collapse duplicate message or just have reference to QueryTracker
    private final @Nullable String _url;
    private final @Nullable StackTraceElement[] _stackTrace; // TODO: collapse duplicate stacktrace or just have reference to QueryTracker

    private final CPUTimer _timer;
    private final long _startOffset;
    private long _duration;

    /** Create a CustomTiming that has been completed (no CPUTimer is started). */
    public CustomTiming(Timing parent, String category, long duration, String message, @Nullable String url, @Nullable StackTraceElement[] stackTrace)
    {
        _parent = parent;
        _duration = duration;
        _category = category;
        _message = message;
        _url = url;
        _stackTrace = stackTrace;

        if (parent != null)
        {
            _startOffset = System.currentTimeMillis() - parent.getRequest().getDate().getTime();
        }
        else
        {
            _startOffset = 0L;
        }

        _timer = null;
    }

    /** Create a CustomTiming that starts a new CPUTimer. */
    public CustomTiming(Timing parent, String category, String message, @Nullable String url)
    {
        this(parent, category, message, url, MiniProfiler.getSettings().isCaptureCustomTimingStacktrace() ? Thread.currentThread().getStackTrace() : null);
    }

    /** Create a CustomTiming that starts a new CPUTimer. */
    public CustomTiming(Timing parent, String category, String message, @Nullable String url, @Nullable StackTraceElement[] stackTrace)
    {
        _parent = parent;
        _category = category;
        _message = message;
        _url = url;
        _stackTrace = stackTrace;

        if (parent != null)
        {
            _startOffset = System.currentTimeMillis() - parent.getRequest().getDate().getTime();
        }
        else
        {
            _startOffset = 0L;
        }

        _timer = new CPUTimer("timer");
        _timer.start();
    }

    @Override
    public void close()
    {
        stop();
    }

    public void stop()
    {
        if (_timer != null && _timer.started())
        {
            _timer.stop();
            _duration = _timer.getTotalMilliseconds();
        }
    }

    public long getStartOffset()
    {
        return _startOffset;
    }

    public long getDuration()
    {
        return _duration;
    }

    @JsonIgnore
    public String getCategory()
    {
        return _category;
    }

    public String getMessage()
    {
        return _message;
    }

    public String getDetailsURL()
    {
        return _url;
    }

    public String getStackTrace()
    {
        if (_stackTrace == null)
            return null;
        return ExceptionUtil.renderStackTrace(_stackTrace);
    }
}
