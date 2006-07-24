/*
 * Copyright (c) 1998-2000 Caucho Technology -- all rights reserved
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
 * @author Emil Ong
 */

package com.caucho.xtpdoc;

import java.io.PrintWriter;
import java.io.IOException;

import java.util.ArrayList;

public class Table implements ContentItem {
  private int _columns = 0;
  private ArrayList<TableRow> _rows = new ArrayList<TableRow>();

  public void addTR(TableRow row)
  {
    _columns = Math.max(_columns, row.getNumberOfColumns());

    _rows.add(row);
  }

  public void writeHtml(PrintWriter writer)
    throws IOException
  {
    writer.println("<table>");

    for (TableRow row : _rows) {
      row.writeHtml(writer);
    }

    writer.println("</table>");
  }

  public void writeLaTeX(PrintWriter writer)
    throws IOException
  {
    writer.print("\\begin{table}");
    writer.print("\\begin{tabular}");

    writer.print("{");

    for (int i = 0; i < _columns; i++)
      writer.print("c");

    writer.print("}");

    for (TableRow row : _rows) {
      row.writeLaTeX(writer);
    }

    writer.print("\\end{tabular}");
    writer.print("\\end{table}");
  }
}
