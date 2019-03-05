/*
 * Copyright (C) 2018 INFORMATION SERVICES INTERNATIONAL - DENTSU, LTD. All Rights Reserved.
 * 
 * Unless you have purchased a commercial license,
 * the following license terms apply:
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.iplass.mtp.impl.auth.oauth.command;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.iplass.mtp.command.Command;
import org.iplass.mtp.command.RequestContext;
import org.iplass.mtp.command.annotation.CommandClass;
import org.iplass.mtp.command.annotation.webapi.WebApi;
import org.iplass.mtp.impl.auth.oauth.MetaOAuthAuthorization.OAuthAuthorizationRuntime;
import org.iplass.mtp.impl.auth.oauth.MetaOAuthClient.OAuthClientRuntime;
import org.iplass.mtp.impl.auth.oauth.OAuthApplicationException;
import org.iplass.mtp.impl.auth.oauth.OAuthConstants;
import org.iplass.mtp.impl.auth.oauth.token.AccessToken;
import org.iplass.mtp.util.StringUtil;
import org.iplass.mtp.webapi.WebApiRequestConstants;
import org.iplass.mtp.webapi.definition.CacheControlType;
import org.iplass.mtp.webapi.definition.MethodType;
import org.iplass.mtp.webapi.definition.RequestType;
import org.iplass.mtp.webapi.definition.StateType;

@WebApi(name="oauth/token",
	accepts=RequestType.REST_FORM,
	methods=MethodType.POST,
	checkXRequestedWithHeader=false,
	privilaged=true,
	state=StateType.STATELESS,
	cacheControlType=CacheControlType.NO_CACHE,
	responseType="application/json"
)
@CommandClass(name="mtp/oauth/TokenCommand", displayName="OAuth2.0 Token Endpoint")
public class TokenCommand implements Command {
	
	static final String PARAM_GRANT_TYPE = "grant_type";
	static final String PARAM_CODE = "code";
	static final String PARAM_REDIRECT_URI ="redirect_uri";
	static final String PARAM_CODE_VERIFIER = "code_verifier";
	static final String PARAM_REFRESH_TOKEN = "refresh_token";
	
	static final String STAT_SUCCESS = "SUCCESS";
	
	@Override
	public String execute(RequestContext request) {
		OAuthClientRuntime clientRuntime = CommandUtil.validateClient(request, true);
		
		String grantType = StringUtil.stripToNull(request.getParam(PARAM_GRANT_TYPE));
		if (OAuthConstants.GRANT_TYPE_AUTHORIZATION_CODE.equals(grantType)) {
			return authorizationCode(request, clientRuntime);
		} else if (OAuthConstants.GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
			return refreshToken(request, clientRuntime);
		} else {
			throw new WebApplicationException(CommandUtil.buildErrorResponse(OAuthConstants.ERROR_UNSUPPORTED_GRANT_TYPE, null, null));
		}
	}
	
	private String authorizationCode(RequestContext request, OAuthClientRuntime clientRuntime) {
		String codeStr = StringUtil.stripToNull(request.getParam(PARAM_CODE));
		if (codeStr == null) {
			throw new WebApplicationException(CommandUtil.buildErrorResponse(OAuthConstants.ERROR_INVALID_REQUEST, "code must specify", null));
		}
		String redirectUri = StringUtil.stripToNull(request.getParam(PARAM_REDIRECT_URI));
		String codeVerifier = StringUtil.stripToNull(request.getParam(PARAM_CODE_VERIFIER));
		
		try {
			OAuthAuthorizationRuntime authRuntime = clientRuntime.getAuthorizationServer();
			AccessToken token = authRuntime.exchangeCodeToToken(codeStr, redirectUri, codeVerifier, clientRuntime);
			ResponseBuilder res = Response.ok().type(MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8")).entity(toResponseEntity(token));
			request.setAttribute(WebApiRequestConstants.DEFAULT_RESULT, res);
			
			return STAT_SUCCESS;
		} catch (OAuthApplicationException e) {
			throw new WebApplicationException(CommandUtil.buildErrorResponse(e.getCode(), e.getDescription(), null));
		}
	}
	
	private Object toResponseEntity(AccessToken accessToken) {
		Map<String, Object> res = new HashMap<>();
		res.put("access_token", accessToken.getTokenEncoded());
		res.put("token_type", OAuthConstants.TOKEN_TYPE_BEARER);
		res.put("expires_in", accessToken.getExpiresIn());
		res.put("scope", CommandUtil.scopeToStr(accessToken.getGrantedScopes()));
		if (accessToken.getRefreshToken() != null) {
			res.put("refresh_token", accessToken.getRefreshToken().getTokenEncoded());
			res.put("refresh_token_expires_in", accessToken.getRefreshToken().getExpiresIn());
		}
		
		return res;
	}

	private String refreshToken(RequestContext request, OAuthClientRuntime clientRuntime) {
		String refreshToken = StringUtil.stripToNull(request.getParam(PARAM_REFRESH_TOKEN));
		if (refreshToken == null) {
			throw new WebApplicationException(CommandUtil.buildErrorResponse(OAuthConstants.ERROR_INVALID_REQUEST, "refresh_token must specify", null));
		}
		
		//refresh token時のscopeのnarrowingおよび、追加のaccess token発行は現状未対応。invalidなaccess tokenの更新のみ可能
		
		try {
			OAuthAuthorizationRuntime authRuntime = clientRuntime.getAuthorizationServer();
			AccessToken token = authRuntime.refreshToken(refreshToken, clientRuntime);
			ResponseBuilder res = Response.ok().type(MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8")).entity(toResponseEntity(token));
			request.setAttribute(WebApiRequestConstants.DEFAULT_RESULT, res);
			
			return STAT_SUCCESS;
		} catch (OAuthApplicationException e) {
			throw new WebApplicationException(CommandUtil.buildErrorResponse(e.getCode(), e.getDescription(), null));
		}
	}
}