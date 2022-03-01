/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ValidationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watson.common.service.base.ServiceBaseLogUtility;
import com.ibm.watson.common.service.base.ServiceConcurrentLimit;
import com.ibm.watson.common.service.base.ServiceContext;
import com.ibm.watson.common.service.base.ServiceContext.AuthenticationType;
import com.ibm.watson.common.service.base.ServiceMetrics;
import com.ibm.watson.common.service.base.ServiceThreadLocal;
import com.ibm.watson.service.base.model.ServiceError;

/**
 * Identifies the tenant, which is the company or organization, with which the
 * user making the current API request is associated and stores that information
 * on the current Thread to be available for downstream processing. When request
 * processing completes, the information is removed from the current thread.
 */
// Need to register filter in ServiceBaseInitialization so we can control order
// @WebFilter(filterName = "MainServletFilter", urlPatterns =
// {"/"+ServiceBaseConstants.SERVICE_API_ROOT+"/*"})
public class MainServletFilter implements Filter {
	private static final Logger logger = LoggerFactory.getLogger(MainServletFilter.class.getName());

	// Config properties
	public static final String TEST_DELAY_PROPERTY = "test_delay_seconds";

	// Main auth header for datapower and deadbolt authentication
	public static final String HEADER_WATSON_USER_INFO = "X-Watson-UserInfo";
	// Sub-fields for DataPower
	public static final String HEADER_WATSON_ORG_ID = "OrgId";
	public static final String HEADER_WATSON_USER_ID = "UserId";
	// Sub-fields for cloud and deadbolt
	public static final String HEADER_CLOUD_BLUEMIX_INSTANCE_ID = "bluemix-instance-id";
	// Main auth header for APIM
	public static final String HEADER_DP_CLIENET_ID = "X-IBM-Client-ID"; // Set by APIM
	// Junit test tenant prefix
	public static final String JUNIT_TENANT_PREFIX = "junit_";

	// Header for delegation token (service to service)
	public static final String HEADER_AUTHORIZATION_TOKEN = "Authorization";

	// Header for service instance parameters object
	public static final String  HEADER_WATSON_INSTANCE_PARAMETERS = "x-watson-instance-parameters";

	// Other header names to pass in ThreadLocal storage
	public static final String HEADER_DP_WATSON_TRAN_ID = "X-DP-Watson-Tran-ID";

	// Correlation ID
	public static final String CORRELATION_ID_KEY="correlationId"; // Value used in log4j.properties pattern
	public static final String CORRELATION_ID_DEFAULT_HEADER = "x-correlation-id";
	// correlationIdHeader is the name of the header to look for in the requests
	// coming in - can be configured via web.xml
	private static String correlationIdHeader = CORRELATION_ID_DEFAULT_HEADER; // Set default

	//tenantArtifactVersionHeader/supertenantArtifactVersionHeader are the to look for in incoming request that contains a tenant/supertenant artifact versions
	public static final String HEADER_TENANT_ARTIFACT_VERSION = "x-tenant-artifact-version";
	public static final String HEADER_SUPER_TENANT_ARTIFACT_VERSION = "x-supertenant-artifact-version";
	
	public static final String HEADER_DEBUG = "x-debug";

	// Cross site support header names
	public static final String HEADER_ACCESS_CONTROL_ORIGIN = "Access-Control-Allow-Origin";
	// @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
	public static final String HEADER_STRICT_TRANSPORT_SECURITY = "strict-transport-security";
	public static final String STRICT_TRANSPORT_SETTING = "max-age=31536000; includeSubDomains";
	public static final String HEADER_CACHE_CONTROL = "cache-control";
	public static final String CACHE_CONTROL_SETTING = "no-store";
	public static final String HEADER_PRAGMA = "pragma";
	public static final String PRAGMA_SETTING = "no-cache";
	
	// A set of security headers recommended to be set for rest apis
	// @see https://owasp.org/www-community/Security_Headers
	// @see https://pages.github.ibm.com/ibmcloud/Security/guidance/secure-coding.html#http-header-best-practices
	private static final String[][] HEADERS_TO_SET =  {
				{"X-XSS-Protection", "0"}, 
				{"X-Content-Type-Options", "nosniff"},
				{"X-Frame-Options", "deny"},          // or SAMEORIGIN if this was going to a web page
				{"Content-Security-Policy", "default-src 'none'"}
		};
		
	

	// Tenant/user keys for log4j logging messages
	private static final String TENANT_ID_KEY="tenantId"; // Value used in log4j.properties pattern
	private static final String USER_ID_KEY="userId";
	//Determines if tenantId coming in should be corrected or normalized to limit which
	//characters and the overall length. Default value is false. Set in top level macro services to true.
	private static boolean normalizeTenantId;
	 

	public static boolean isNormalizeTenantId() {
		return normalizeTenantId;
	}

	public static void setNormalizeTenantId(boolean normalizeTenantId) {
		MainServletFilter.normalizeTenantId = normalizeTenantId;
	}
	// Member variables
	protected ServiceBaseLogUtility logUtility;
	protected ServiceConcurrentLimit concurrentLimit;
	protected long testDelaySeconds;


	private class TenantImpl implements Tenant {

		private String tenantId_;
		private String userId_;

		private TenantImpl(String tenantId, String userId) {
			tenantId_ = tenantId;
			userId_ = userId;
		}

		@Override
		public String getTenantId() {
			return tenantId_;
		}

		@Override
		public String getUserId() {
			return userId_;
		}

	}

	@Override
	public void init(FilterConfig config) throws ServletException {
		// Don't allow this init method to fail.  It will not load and
		// will not get passed filter requests.  We need this filter in place
		// to set the possible tenant of the request.  If this init() method
		// thows an exception, it will be caught, logged, and the service will
		// get marked as initialization failed.
		try {
			logger.info("Initializing filter:"+this.getClass().getName());
			logger.info("correlationIdHeader=" + correlationIdHeader);

			logUtility = ServiceBaseLogUtility.getInstance();
			concurrentLimit = ServiceContext.getConcurrentLimit();

	    	// Load test delay property if present.  This is used to help with chaos testing
	    	Properties serviceProperties = ServiceContext.getInstance().getServiceProperties();
	    	String testDelayProperty = serviceProperties.getProperty(TEST_DELAY_PROPERTY);
	    	if(testDelayProperty != null) {
	    		testDelaySeconds = Long.parseLong(testDelayProperty);
	    	}
		}
		catch(Throwable e) {
			logger.error("Error, MainServletFilter initialization failed.  Service set to 'initialization failed' state.", e);
			ServiceContext.getInstance().setInitializationFailed(true);
		}
	}

	@Override
	public void destroy() {
		// nothing required here
	}

	@Override
	public void doFilter(
			ServletRequest request,
			ServletResponse response,
			FilterChain filterChain) throws IOException, ServletException {

        long initialTime = System.currentTimeMillis();

// TODO - not sure if this check is needed
//		if (!(request instanceof HttpServletRequest)) {
//			// don't pass on non-HTTP requests - shouldn't get any
//			logger.log(Level.ERROR, "non-HTTP request");
//			return;
//		}

		ServiceContext serviceContext = ServiceContext.getInstance();
		ServiceMetrics serviceMetrics = serviceContext.getServiceMetrics();

        ServiceContext.incrementRequestCount(); // Count the number of REST calls

		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse)response;

		try {
			cleanupThreadLocal();

			// Add correlation ID to ThreadLocal and MDC for logging
			processCorrelationId(httpRequest, httpResponse);

			// Add request ID to ThreadLocal
			ServiceThreadLocal.setRequestId(UUID.randomUUID().toString());

			//Add the tenant artifact version from the input header if it exists.
			processTenantArtifactVersion(httpRequest);

			//Set the debug flag from the header if it exists
			processDebugHeader(httpRequest);

			// Add cross site headers in response
			processAccessControl(httpResponse);

			//  Determine authorization type
			AuthenticationType authType = serviceContext.getAuthenticationType();
			if (authType == null) {
				// If authType is null, service initialization failed.
				// Reject all requests
				logger.error("Authentication type not configured - returning 500");
				httpResponse.setStatus(Status.INTERNAL_SERVER_ERROR.getStatusCode());
				httpResponse.setContentType(MediaType.APPLICATION_JSON);
				ServiceError se = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode());
				se.setMessage(Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
				se.setDescription("Authentication type not configured");
				ObjectMapper om = new ObjectMapper();
				om.setSerializationInclusion(Include.NON_NULL);
				httpResponse.getWriter().print(om.writeValueAsString(se));
				// Do not pass on request
				return;
			}

			// Get tenant ID and user ID from headers based on authType
			Tenant tenant = buildTenant(authType, serviceContext, httpRequest);
			String tenantId = tenant.getTenantId();
			String tenantUserId = tenant.getUserId();

			TenantManager.setTenant(tenant);  // Save tenant info in thread local
			if (logger.isDebugEnabled()) {
				logger.debug("tenantId="+tenantId+" userId="+tenantUserId);
			}

			// Add tenantId and userId to log4j MDC (thread local)
			if (tenantId != null && !tenantId.isEmpty()) {
				MDC.put(TENANT_ID_KEY, tenantId);
			}
			if (tenantUserId != null && !tenantUserId.isEmpty()) {
				MDC.put(USER_ID_KEY, tenantUserId);
			}

			// Save specific headers in thread local storage for later use
			// The headers to copy could become a service parameter
			String HEADERS_TO_COPY[];
			if (serviceContext.getAuthenticationHeaderName() != null && !serviceContext.getAuthenticationHeaderName().isEmpty()) {
				HEADERS_TO_COPY= new String[]{HEADER_WATSON_USER_INFO, HEADER_DP_CLIENET_ID, HEADER_DP_WATSON_TRAN_ID, HEADER_AUTHORIZATION_TOKEN, HEADER_WATSON_INSTANCE_PARAMETERS, serviceContext.getAuthenticationHeaderName()};
			}else {
				HEADERS_TO_COPY = new String[]{HEADER_WATSON_USER_INFO, HEADER_DP_CLIENET_ID, HEADER_DP_WATSON_TRAN_ID, HEADER_AUTHORIZATION_TOKEN, HEADER_WATSON_INSTANCE_PARAMETERS};
			}
			Map<String,String> headerMap = new HashMap<>();
			Enumeration<String> headerNames = httpRequest.getHeaderNames();
			int matchCount = 0;
			while(headerNames.hasMoreElements() && matchCount < HEADERS_TO_COPY.length) {
				String headerName = headerNames.nextElement();
				// Do simple linear search for headers to copy.  May want to do a map lookup if list gets large
				for(String matchItem : HEADERS_TO_COPY) {
					if(headerName.equals(matchItem)) {
						matchCount++;
						headerMap.put(matchItem, httpRequest.getHeader(matchItem));
						break;
					}
				}
			}
			if(headerMap.size() > 0) {
				ServiceThreadLocal.setRequestHeaders(headerMap);
			}

			// Log request entry and headers if not filtered
			boolean headerLogged = false;
			if(!logUtility.filterLog(httpRequest.getPathInfo())) {
				logUtility.logRequest(httpRequest);
				logUtility.logRequestHeader(httpRequest);
		        headerLogged = true;
			}

			// Testing only - add sleep to simulate log API call if enabled via system property
			// Currently only delays the status endpoints
			if(testDelaySeconds > 0) {
				if(httpRequest.getPathInfo().startsWith("/v1/status")) {
					try {
						Thread.sleep(testDelaySeconds*1000);
					}
					catch(InterruptedException e) {
						// Ignore
					}
				}
			}

			// Reject all incoming requests if service initialization has failed
			if(ServiceContext.getInstance().getInitializationFailed()) {
				logger.error("Service initialization failed.  Cannot complete request, returning 500");
				((HttpServletResponse) response).setStatus(Status.INTERNAL_SERVER_ERROR.getStatusCode());
				((HttpServletResponse) response).setContentType(MediaType.APPLICATION_JSON);
				ServiceError se = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode());
				se.setMessage(Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
				se.setDescription("Service initialization failed");
				ObjectMapper om = new ObjectMapper();
				om.setSerializationInclusion(Include.NON_NULL);
				((HttpServletResponse) response).getWriter().print(om.writeValueAsString(se));
				// Do not pass on request
			}
//			else if (serviceContext.getAuthenticationType() != AuthenticationType.legacy &&
//					(tenantId == null || tenantId.isEmpty()) ) {
//				// An authentication header is required if not running in legacy mode
//				logger.log(Level.ERROR, "Error, authentication header not provided - returning 401");
//				httpResponse.setStatus(Status.UNAUTHORIZED.getStatusCode());
//				httpResponse.setContentType(MediaType.APPLICATION_JSON);
//				ServiceError se = new ServiceError(Status.UNAUTHORIZED.getStatusCode());
//				se.setMessage(Status.UNAUTHORIZED.getReasonPhrase());
//				se.setDescription("Authentication header not provided");
//				ObjectMapper om = new ObjectMapper();
//				om.setSerializationInclusion(Include.NON_NULL);
//				httpResponse.getWriter().print(om.writeValueAsString(se));
//				// Request will not get passed on
//			}
			else {
				// Pass on request unless concurrent limit has been met
				boolean acquireGranted = false;
				try {
					boolean allowRequest = true;
					// Limit access based on uri
					if(concurrentLimit.shouldLimitRequest(httpRequest.getPathInfo())) {
						acquireGranted = concurrentLimit.acquireRequest(); // May block
						allowRequest = acquireGranted;
					}

					// Process request if not at the concurrent request maximum or limit check should be skipped
					if(allowRequest) {
						if (serviceMetrics != null) serviceMetrics.filterEntry(httpRequest, httpResponse);
						// Execute downstream filters ============
						filterChain.doFilter(request, response);
					}
					else {
						// Too many concurrent requests, return unavailable status
						httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
						httpResponse.setContentType(MediaType.APPLICATION_JSON);
						ServiceError se = new ServiceError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
						se.setDescription("Too many concurrent requests, "+concurrentLimit.getConcurrentRejectThreshold());
						ObjectMapper om = new ObjectMapper();
						httpResponse.getWriter().print(om.writeValueAsString(se));
					}
				}
				finally {
					// Free up a request if acquired
					if(acquireGranted) {
						concurrentLimit.releaseRequest();
					}
				}
			}

			// Process response ======================
			// Post processing code here..

			// Log request headers on non 2xx responses and header not already logged.
			// This will occur if the log request was filtered.  We still want the headers on errors.
			if(!headerLogged && (httpResponse.getStatus() >= 300)) {
				logUtility.logRequestHeader(httpRequest);
			}

	        // Log response with API time
	        double elapsedSecs = (System.currentTimeMillis() - initialTime) / 1000.0;
	        logUtility.logResponse(httpRequest, httpResponse, elapsedSecs);
	        if (serviceMetrics != null) serviceMetrics.filterExit(httpRequest, httpResponse, elapsedSecs);

		} catch (RuntimeException re) {
			if (re instanceof IllegalArgumentException) {
				logger.error(String.valueOf(HttpServletResponse.SC_BAD_REQUEST), re);
				httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				httpResponse.setContentType(MediaType.APPLICATION_JSON);
				ServiceError se = new ServiceError(HttpServletResponse.SC_BAD_REQUEST);
				se.setMessage("Illegal character found in request");
				se.setDescription("The specified URL could not be resolved due to illegal characters.");
				se.setMoreInfo(re.getMessage());
				ObjectMapper om = new ObjectMapper();
				om.setSerializationInclusion(Include.NON_NULL);
				httpResponse.getWriter().print(om.writeValueAsString(se));
			} else {
				logger.error(String.valueOf(HttpServletResponse.SC_NOT_ACCEPTABLE), re);
				httpResponse.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
				httpResponse.setContentType(MediaType.APPLICATION_JSON);
				ServiceError se = new ServiceError(HttpServletResponse.SC_NOT_ACCEPTABLE);
				se.setMessage("Request could not be completed at this time, check syntax");
				se.setDescription("The request could not be resolved to a published API.");
				se.setMoreInfo(re.getMessage());
				ObjectMapper om = new ObjectMapper();
				om.setSerializationInclusion(Include.NON_NULL);
				httpResponse.getWriter().print(om.writeValueAsString(se));
			}
		} finally {
			// Clean up ThreadLocal objects
			cleanupThreadLocal();
		}
	}

	/**
	 * Cleanup any thread local objects
	 */
	protected void cleanupThreadLocal() {
		TenantManager.setTenant(null);
		ServiceThreadLocal.doCleanup();
		MDC.remove(CORRELATION_ID_KEY);
		MDC.remove(TENANT_ID_KEY);
		MDC.remove(USER_ID_KEY);
	}

	/**
	 * Obtains the value of the hosting site security header.
	 *
	 * @param httpServletRequest the request this filter instance is processing
	 *
	 * @return the header value of <i>null</i> if the header was not supplied
	 */
	protected String getUserInfoHeaderValue(HttpServletRequest httpServletRequest) {

		String value = httpServletRequest.getHeader(HEADER_WATSON_USER_INFO);

		if (value != null) {
			value = value.trim();
			if (value.isEmpty()) {
				value = null;
			}
		}

		return value;
	}

	/**
	 * Get a a sub-property of the given header value string.
	 *
	 * @param headerValue The complete value of the custom HTTP security header
	 * @param propertyName The property name within the header value
	 *
	 * @return The property value trimmed of whitespace or <i>null</i> if
	 * property is not found
	 */
	protected String getPropertyFromHeaderValue(String headerValue, String propertyName) {

		String propertyValue = null;

		if (headerValue != null) {
			StringTokenizer tokenizer = new StringTokenizer(headerValue, ";");
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				StringTokenizer propertyTokenizer = new StringTokenizer(token, "=");
				// tokens with multiple equals signs are not valid
				if (propertyTokenizer.countTokens() == 2) {
					String name = propertyTokenizer.nextToken().trim();
					String value = propertyTokenizer.nextToken();
					if (name.equals(propertyName)) {
						propertyValue = value.trim();
						break;
					}
				}
			}
		}

		return propertyValue;
	}

//	public static String buildUserInfoHeader(Tenant tenant) {
//			String userId = tenant.getUserId();
//			String tenantId = tenant.getTenantId();
//			// example: X-Watson-UserInfo:"UserId=myuserid;OrgId=IBM;
//
//			StringBuffer sb = new StringBuffer();
//			if(userId != null && !userId.isEmpty()) {
//				sb.append(HEADER_WATSON_USER_ID+"="+userId+";");
//			}
//			if(tenantId != null && !tenantId.isEmpty()) {
//				sb.append(HEADER_WATSON_ORG_ID+"="+tenantId+";");
//			}
//
//			String userInfo = null;
//			if(sb.length() > 0) {
//				userInfo = sb.toString();
//			}
//
//			return userInfo;
//	}

	private void processCorrelationId(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		String correlationId = httpRequest.getHeader(correlationIdHeader);
		if (StringUtils.containsAny((CharSequence)correlationId, (CharSequence)"<[*\n\r\\")) {
			throw new ValidationException("Invalid value for " + correlationId);
		}
		if (correlationId != null) {
			MDC.put(CORRELATION_ID_KEY, correlationId);
		} else {
			// Generate a correlation ID
			correlationId = UUID.randomUUID().toString();
			MDC.put(CORRELATION_ID_KEY, correlationId);
		}
		// Set correlation ID in response header if not already present
		String responseCorrelationId = httpResponse.getHeader(correlationIdHeader);
		if(responseCorrelationId == null) {
			httpResponse.setHeader(correlationIdHeader, correlationId);
		}
		// Also put correlation ID in ServiceThreadLocal
		ServiceThreadLocal.setCorrelationId(correlationId);
	}

	/**
	 * Sets the tenant artifact version (if there is one in the request header) into ServiceThreadLocal.
	 *
	 * ACD will set this in the header for each microservice call.  The primary use of this is to allow the artifact store cache
	 * to know if it's cache is current without having to make a network call for each artifact type in each microservice
	 * which can get expensive.
	 *
	 * @param httpRequest
	 */
	private void processTenantArtifactVersion(HttpServletRequest httpRequest) {
		String tenantArtifactVersion = httpRequest.getHeader(HEADER_TENANT_ARTIFACT_VERSION);
		//Set the tenant artifact version if we have one, otherwise it'll be null
		if (tenantArtifactVersion != null) {
			ServiceThreadLocal.setTenantArtifactVersion(tenantArtifactVersion);
		}

		String superTenantArtifactVersion = httpRequest.getHeader(HEADER_SUPER_TENANT_ARTIFACT_VERSION);
		if (superTenantArtifactVersion != null) {
			ServiceThreadLocal.setSuperTenantArtifactVersion(superTenantArtifactVersion);
		}
	}
	
	/**
	 * Sets the debug flag for this thread into ServiceTheadLocal.  
	 * 
	 * ACD will receive this as a request parameter and then propagate it to all of the microservices via request headers
	 * @param httpRequest
	 */
	private void processDebugHeader(HttpServletRequest httpRequest) {
	  String debug = httpRequest.getHeader(HEADER_DEBUG);
	  if (debug != null) {
	    try {
	      Boolean parsedDebug = Boolean.parseBoolean(debug.trim());
	      ServiceThreadLocal.setThreadDebug(parsedDebug);
	    }
	    catch (Exception e) {
	      //Eat it.  If they pass a bogus debug header, it's not important enough to log as this is for internal use only.
	      ServiceThreadLocal.setThreadDebug(false);
	    }
	    
	  }
	  else {
	    ServiceThreadLocal.setThreadDebug(false);
	  }
	}

	private void processAccessControl(HttpServletResponse httpResponse) {
		// Add Access control origin for cross site support
/*		if(httpResponse.getHeader(HEADER_ACCESS_CONTROL_ORIGIN) == null) {
			httpResponse.setHeader(HEADER_ACCESS_CONTROL_ORIGIN, "*");
			if(logger.isDebugEnabled()) logger.debug("Adding header: "+HEADER_ACCESS_CONTROL_ORIGIN);
		}
*/
		if (httpResponse.getHeader(HEADER_STRICT_TRANSPORT_SECURITY) == null){
			httpResponse.setHeader(HEADER_STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SETTING);
		}

		if (httpResponse.getHeader(HEADER_CACHE_CONTROL) == null){
			httpResponse.setHeader(HEADER_CACHE_CONTROL, CACHE_CONTROL_SETTING);
		}

		if (httpResponse.getHeader(HEADER_PRAGMA) == null){
			httpResponse.setHeader(HEADER_PRAGMA, PRAGMA_SETTING);
		}
		// add security headers to all responses
		for (String[] header : HEADERS_TO_SET) {
			httpResponse.setHeader(header[0], header[1]);
	    }
		
	}

	private Tenant buildTenant(AuthenticationType authType, ServiceContext serviceContext, HttpServletRequest httpRequest) {
		String threadTenantId = null;
		String threadUserId = null;
		String userInfoHeader = null;

		switch(authType) {
			case legacy:
				// First check for X-Watson-UserInfo:bluemix-instance-id (From deadbolt)
				userInfoHeader = getUserInfoHeaderValue(httpRequest);
				threadTenantId = getPropertyFromHeaderValue(userInfoHeader, HEADER_CLOUD_BLUEMIX_INSTANCE_ID);

				// If not found, try to get tenant ID from X-IBM-Client-ID (From APIM)
				if(threadTenantId == null) {
					String clientId = httpRequest.getHeader(HEADER_DP_CLIENET_ID);
					if(clientId != null) {
						clientId = clientId.trim();
						if(!clientId.isEmpty()) {
							threadTenantId = clientId;
						}
					}
				}
				// If not found, try to use X-Watson-UserInfo:orgId (From datapower)
				if(threadTenantId == null) {
					threadTenantId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_ORG_ID);
					threadUserId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_USER_ID);
				}
				break;
			case apim:
				// Get tenant ID from header X-IBM-Client-ID
				String clientId = httpRequest.getHeader(HEADER_DP_CLIENET_ID);
				if(clientId != null) {
					clientId = clientId.trim();
					if(!clientId.isEmpty()) {
						threadTenantId = clientId;
					}
				}
				threadUserId = null;
				break;
			case datapower:
				// Get tenant ID and user ID from header X-Watson-UserInfo
				userInfoHeader = getUserInfoHeaderValue(httpRequest);
				threadTenantId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_ORG_ID);
				threadUserId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_USER_ID);
				break;
			case datapower_test:
				// Get tenant ID and user ID from header X-Watson-UserInfo
				// Allow for tenant override
				userInfoHeader = getUserInfoHeaderValue(httpRequest);
				threadTenantId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_ORG_ID);
				threadUserId = getPropertyFromHeaderValue(userInfoHeader, HEADER_WATSON_USER_ID);

				// Override tenant ID if X-IBM-Client-ID is set.  This can be a security exposure.
				// This is used for DET testing
				String clientIdOverride = httpRequest.getHeader(HEADER_DP_CLIENET_ID);
				if(clientIdOverride != null) {
					clientIdOverride = clientIdOverride.trim();
					if(!clientIdOverride.isEmpty()) {
						threadTenantId = clientIdOverride;
					}
				}
				break;
			case custom_header:
				// Get tenant ID from header name specified in authentication_header property
				String customHeaderName = serviceContext.getAuthenticationHeaderName();
				if(customHeaderName != null) {
					threadTenantId = httpRequest.getHeader(customHeaderName);
				}
				threadUserId = null;
				break;
			case deadbolt:
				// Get tenant ID from header X-Watson-UserInfo:bluemix-instance-id
				userInfoHeader = getUserInfoHeaderValue(httpRequest);
				threadTenantId = getPropertyFromHeaderValue(userInfoHeader, HEADER_CLOUD_BLUEMIX_INSTANCE_ID);
				threadUserId = null;
				break;
			default:
				// Should not get here
				break;
		}
		
		if (normalizeTenantId) {
			threadTenantId = normalizeTenantId(threadTenantId);
		}

		return new TenantImpl(threadTenantId, threadUserId);
	}
	/**
	 * @param tenantId
	 * @return tenantId which is urlEncoded ( replacing  % with !,+ with !! , @ with *, and no empty  spaces.
	 *  If the resulting urlEncoded tenantId is greater than 128 take the first 63 characters and append it 
	 *  with message digest using SHA 256algorithm.
	 *  Period characters at the end of the ID are removed as they are not compatible with some artifactstore back ends.
	 *  Throws exception in case of any issues
	 */ 
	private String normalizeTenantId(String tenantId) {
		if(logger.isDebugEnabled()) logger.debug("Input tenantId : {}", tenantId);
		try {
			if (tenantId!=null && !tenantId.isEmpty()){
				String encodedTenantId = URLEncoder.encode(tenantId, "UTF-8");
				tenantId = encodedTenantId.replace('%', '!').replace("+", "!!").replace('@', '*')
						.replaceAll("\\s", "").trim();
				
				// Remove ending period characters on the tenant ID
				while(tenantId.endsWith(".")) {
					// Remove ending period
					tenantId = tenantId.substring(0, tenantId.length()-1);
				}
				if (tenantId.isEmpty()) {
					// Tenant ID only contained periods - throw an exception
					throw new InvalidParameterException("Tenant ID cannot contain only periods");
				}
				
				if (tenantId.length() > 128) {
					MessageDigest md = MessageDigest.getInstance("SHA-256");
					tenantId = tenantId.substring(0, 63) + "_" +
							md.digest(tenantId.getBytes("UTF-8"));
					logger.debug("Truncated tenantId : {}", tenantId);
				}
			}
			if(logger.isDebugEnabled()) logger.debug("Normalized tenantId : {}", tenantId);
		} catch (Exception  e) {
			// Exception will get logged at higher catch
			throw new RuntimeException(e);
		}
		
		return tenantId;
	}

}
