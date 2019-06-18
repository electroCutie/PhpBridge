package cloud.literallya.phpBridge;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Servlet example of the PHP Bridge
 * 
 */
@WebServlet("/PhpBridge")
public class PhpBridge extends HttpServlet{
  private static final long serialVersionUID = 1L;

  private static final BridgeHead bridge = new BridgeHead();

  /**
   * This is the implementation. The Bridge uses Put requests
   */
  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException{
    bridge.handleRequest(req, resp);
  }

  /*
   * get and post are unused
   */

  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    response.getOutputStream().write('1');
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    response.getOutputStream().write('1');
  }

}
