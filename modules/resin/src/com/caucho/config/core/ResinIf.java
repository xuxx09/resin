/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

package com.caucho.config.core;

import com.caucho.util.L10N;

import com.caucho.config.BuilderProgram;
import com.caucho.config.BuilderProgramContainer;
import com.caucho.config.ConfigException;

/**
 * Sets an EL value.
 */
public class ResinIf extends ResinControl {
  private static final L10N L = new L10N(ResinIf.class);

  private BuilderProgramContainer _init = new BuilderProgramContainer();

  private boolean _test = false;
  
  /**
   * Set true if the contents should be applied.
   */
  public void setTest(boolean value)
  {
    _test = value;
  }

  /**
   * Adds to the builder program.
   */
  public void addBuilderProgram(BuilderProgram program)
  {
    _init.addProgram(program);
  }

  public void init()
    throws Throwable
  {
    Object object = getObject();
    
    if (_test && object != null)
      _init.configure(object);
  }
}

