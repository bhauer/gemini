/*******************************************************************************
 * Copyright (c) 2018, TechEmpower, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name TechEmpower, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL TECHEMPOWER, INC. BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/

package com.techempower.gemini.monitor;

import java.sql.*;

import com.techempower.data.*;
import com.techempower.data.jdbc.*;
import com.techempower.gemini.*;
import com.techempower.log.*;
import com.techempower.util.*;

/**
 * Listens to database and Dispatcher events for the Gemini application-
 * monitoring functionality.  For database monitoring, this class sends "do
 * nothing" signals in response to exception notifications since this is 
 * a passive listener.  Should be used with a DatabaseConnectionListenerList
 * so that an existing listener can be used as well.
 *   <p>
 * To be clear: the MonitorListener is a component used by the GeminiMonitor
 * to listen to events occurring within the web application.  This is 
 * <b>not</b> an interface used to listen to the GeminiMonitor itself.  For
 * that need, see GeminiMonitorListener.
 *
 * @see GeminiMonitorListener
 */
public class MonitorListener 
  implements DatabaseConnectionListener,
             DispatchListener,
             RequestListener
{

  //
  // Constants.
  //
  
  public static final String COMPONENT_CODE = "MonL";

  //
  // Member variables.
  //
  
  private final GeminiMonitor monitor;
  private final ComponentLog  log;
  
  //
  // Member methods.
  //
  
  /**
   * Constructor.
   */
  public MonitorListener(GeminiMonitor monitor)
  {
    this.monitor = monitor;
    this.log = monitor.getApplication().getLog(COMPONENT_CODE);
  }
  
  //
  // DatabaseConnectionListener interface.
  //
  
  @Override
  public int exceptionInExecuteBatch(SQLException exc, JdbcConnector conn)
  {
    if (monitor.isEnabled())
    {
      MonitorSample.get().queryException();
    }
    return INSTRUCT_DO_NOTHING;
  }

  @Override
  public int exceptionInRunQuery(SQLException exc, JdbcConnector conn)
  {
    if (monitor.isEnabled())
    {
      MonitorSample.get().queryException();
    }
    return INSTRUCT_DO_NOTHING;
  }

  @Override
  public int exceptionInRunUpdateQuery(SQLException exc, JdbcConnector conn)
  {
    if (monitor.isEnabled())
    {
      MonitorSample.get().queryException();
    }
    return INSTRUCT_DO_NOTHING;
  }

  @Override
  public void queryStarting()
  {
    if (monitor.isEnabled())
    {
      // Notify the performance monitor sample.
      MonitorSample.get().queryStarting();
  
      // Notify the health monitor.
      monitor.queryStarting();
    }
  }

  @Override
  public void queryCompleting()
  {
    if (monitor.isEnabled())
    {
      // Notify the performance monitor sample.
      MonitorSample.get().queryCompleting();
      
      // Notify the health monitor.
      monitor.queryCompleting();
    }
  }

  @Override
  public void configure(EnhancedProperties props)
  {
    // Does nothing.
  }

  //
  // DispatchListener interface.
  //
  
  @Override
  public void dispatchStarting(Dispatcher dispatcher, Context context, String command)
  {
    if (monitor.isEnabled())
    {
      MonitorSample sample = MonitorSample.get();
      
      // Notify the performance monitor sample.
      boolean first = sample.dispatchStarting(command);
      
      // Notify the health monitor.
      if (first)
      {
        monitor.addRequest(MonitorSample.get());
        sample.setRequestNumber(context.getRequestNumber());
        monitor.dispatchStarting(sample, command);
      }
    }
  }

  @Override
  public void redispatchOccurring(Dispatcher dispatcher,
    Context context, String previousCommand, String newCommand)
  {
    if (monitor.isEnabled())
    {
      // Consider the previous command ending and the new command starting.
      dispatchComplete(dispatcher, context);
      dispatchStarting(dispatcher, context, newCommand);
    }
  }

  @Override
  public void dispatchComplete(Dispatcher dispatcher, Context context)
  {
    if (monitor.isEnabled())
    {
      final MonitorSample sample = MonitorSample.get();
      
      // This is considered the final action to fulfill a request, so we 
      // process here.  We first call logicComplete so that we capture a logic 
      // completion even if there was no render component (e.g. an AJAX/JSON
      // request).
      sample.logicComplete();

      // Capture the CPU time at the start of the request.
      sample.setCpuTimeAtEnd(monitor.getCurrentThreadCpuTime());

      // Ask the monitor to capture/process this sample.
      try
      {
        monitor.process(sample, context);
      }
      catch (Exception exc)
      {
        // Get this logged if it happens.
        log.log("Exception while processing MonitorSample.", LogLevel.ALERT, exc);
      }
      finally
      {
        monitor.removeRequest(sample);
        MonitorSample.complete();
      }
      
      // Notify the health monitor.
      monitor.dispatchComplete();
    }
  }

  @Override
  public void renderComplete(Dispatcher dispatcher, Context context)
  {
    if (monitor.isEnabled())
    {
      // Notify the performance monitor sample.
      MonitorSample.get().renderComplete();
      
      // Notify the health monitor.
      monitor.jspComplete();
    }
  }

  @Override
  public void renderStarting(Dispatcher dispatcher, String jspName)
  {
    if (monitor.isEnabled())
    {
      // Notify the performance monitor sample.
      MonitorSample.get().logicComplete();
  
      // Notify the health monitor.
      monitor.jspIncluded();
    }
  }

  //
  // RequestListener methods.
  //
  
  @Override
  public void requestStarting(Context request)
  {
    if (monitor.isEnabled())
    {
      // Calling MonitorSample.get() sets up the initial timers (request start
      // time specifically) by way of a new MonitorSample being constructed
      // and attached to the current thread.
      
      // Capture the CPU time at the start of the request.
      MonitorSample.get().setCpuTimeAtStart(monitor.getCurrentThreadCpuTime());
    }
  }

  @Override
  public void requestCompleting(Context context)
  {
    if (monitor.isEnabled())
    {
      // Although the reference to the current sample is removed by the call
      // to dispatchComplete (above), we call complete again here for certainty.
      MonitorSample.complete();
    }
  }

}  // End MonitorListener.
