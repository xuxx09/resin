/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.env.thread;

import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.env.shutdown.ExitCode;
import com.caucho.env.shutdown.ShutdownService;
import com.caucho.inject.Module;

@Module
class ThreadLauncher extends AbstractThreadLauncher {
  private static final int DEFAULT_PRIORITY_IDLE_MIN = 2;
  
  private final ThreadPool _pool;
  
  private int _priorityIdleMin;
  
  private final AtomicInteger _priorityIdleCount = new AtomicInteger();

  ThreadLauncher(ThreadPool pool)
  {
    _pool = pool;
    
    setPriorityIdleMin(DEFAULT_PRIORITY_IDLE_MIN);
  }
  
  public void setPriorityIdleMin(int min)
  {
    if (min <= 0)
      throw new IllegalArgumentException();
    
    _priorityIdleMin = min;
    
    update();
  }
  
  public int getPriorityIdleMin()
  {
    return _priorityIdleMin;
  }
  
  protected boolean beginPriorityIdle()
  {
    int priorityIdle = _priorityIdleCount.get();
  
    if (priorityIdle < _priorityIdleMin
        && _priorityIdleCount.compareAndSet(priorityIdle, priorityIdle + 1)) {
      return true;
    }
    else
      return false;
  }
  
  protected void onPriorityIdleEnd()
  {
    int priorityIdleCount = _priorityIdleCount.getAndDecrement();
    
    if (priorityIdleCount < _priorityIdleMin) {
      wake();
    }
  }

  @Override
  protected boolean isIdleTooLow(int startingCount)
  {
    int surplus = _priorityIdleCount.get() - _priorityIdleMin;
    
    if (surplus >= 0)
      return super.isIdleTooLow(startingCount);
    
    startingCount += surplus;
    
    if (startingCount < 0)
      return true;
    else
      return super.isIdleTooLow(startingCount);
  }

  @Override
  protected void startWorkerThread()
  {
    Thread thread = new Thread(this);
    thread.setDaemon(true);
    thread.setName("resin-thread-launcher");
    thread.start();
  }
  
  @Override
  protected long getCurrentTimeActual()
  {
    return System.currentTimeMillis();
  }

  @Override
  protected void launchChildThread(int id)
  {
    try {
      ResinThread poolThread = new ResinThread(id, _pool, this);
      poolThread.start();
    } catch (Throwable e) {
      e.printStackTrace();

      String msg = "Resin exiting because of failed thread";

      ShutdownService.shutdownActive(ExitCode.THREAD, msg);
    }
  }
  
  //
  // statistics
  //
  
  public int getPriorityIdleCount()
  {
    return _priorityIdleCount.get();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pool + "]";
  }
}