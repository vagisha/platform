/*
 * This file is part of "SnipSnap Wiki/Weblog".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://snipsnap.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 */

package org.radeox.test.macro;

import junit.framework.TestCase;
import org.radeox.engine.context.BaseRenderContext;
import org.radeox.api.engine.context.RenderContext;

/**
 * 
 * @author 
 * @version $Id: MacroTestSupport.java,v 1.2 2003/10/07 08:20:24 stephan Exp $
 */

public class MacroTestSupport extends TestCase {
  protected RenderContext context;

  public MacroTestSupport(String s) {
    super(s);
  }

  protected void setUp() throws Exception {
    context = new BaseRenderContext();
    super.setUp();
  }
}
