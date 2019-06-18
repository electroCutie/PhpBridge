package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

public class BridgeHead{

  public BridgeHead(){
  }

  private final Dispatch dispatch = new Dispatch();
  private final FieldGetterSetter attrs = new FieldGetterSetter();

  private static void checkIsLoopbackAddress(HttpServletRequest request) throws UnknownHostException{
    checkState(InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress(),
      "request from bad host: %s", request.getRemoteAddr());
  }

  public void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException{
    checkIsLoopbackAddress(req);

    resp.setStatus(200);
    new Session(req.getInputStream(), resp.getOutputStream(), dispatch, attrs).handleConnection();
  }

}
