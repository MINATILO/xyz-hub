/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.auth;

import com.here.xyz.hub.Service;
import com.here.xyz.hub.auth.Authorization.AuthorizationType;
import com.here.xyz.hub.rest.ApiParam.Query;
import com.here.xyz.hub.util.Compression;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.JWTAuthHandlerImpl;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import org.apache.commons.lang3.StringUtils;

public class ExtendedJWTAuthHandler extends JWTAuthHandlerImpl {

  /**
   * Indicates, if compressed JWTs are allowed.
   */
  final boolean ALLOW_COMPRESSED_JWT = true;

  /**
   * Indicates, if the bearer token could be send in the request URI query component as defined in <a
   * href="https://datatracker.ietf.org/doc/html/rfc6750#section-2.3">RFC-6750 Section 2.3</a>
   */
  final boolean ALLOW_URI_QUERY_PARAMETER = true;

  /**
   * Indicates, if anonymous access is allowed.
   */
  final boolean ALLOW_ANONYMOUS_ACCESS = Service.configuration.XYZ_HUB_AUTH == AuthorizationType.DUMMY;

  private static final String ANONYMOUS_JWT_RESOURCE_FILE = "/auth/dummyJwt.json";
  private static final String ANONYMOUS_JWT = JwtGenerator.generateToken(ANONYMOUS_JWT_RESOURCE_FILE);

  public ExtendedJWTAuthHandler(JWTAuth authProvider, String realm) {
    super(authProvider, realm);
  }

  @Override
  public void authenticate(RoutingContext context, Handler<AsyncResult<User>> handler) {
    String jwt = getFromAuthHeader(context.request().headers().get(HttpHeaders.AUTHORIZATION));

    // Try to get the token from the query parameter
    if (ALLOW_URI_QUERY_PARAMETER && jwt == null) {
      final List<String> accessTokenParam = Query.queryParam(Query.ACCESS_TOKEN, context);
      if (accessTokenParam != null && accessTokenParam.size() > 0) {
        jwt = accessTokenParam.get(0);
      }
    }

    // If anonymous access is allowed, use the default anonymous JWT token
    if (ALLOW_ANONYMOUS_ACCESS && jwt == null) {
      jwt = ANONYMOUS_JWT;
    }

    // If compressed JWTs are supported≥
    if (ALLOW_COMPRESSED_JWT && jwt != null && !isJWT(jwt)) {
      try {
        byte[] bytearray = Base64.getDecoder().decode(jwt.getBytes());
        bytearray = Compression.decompressUsingInflate(bytearray);
        jwt = new String(bytearray);
      } catch (DataFormatException e) {
        handler.handle(Future.failedFuture("Wrong auth credentials format."));
        return;
      }
    }

    if (jwt != null) {
      context.request().headers().set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }

    super.authenticate(context, handler);
  }

  private String getFromAuthHeader(String authHeader) {
    return (authHeader != null && authHeader.startsWith("Bearer ")) ?
        authHeader.substring(7) : null;
  }

  private boolean isJWT(final String jwt) {
    return StringUtils.countMatches(jwt, ".") == 2;
  }
}