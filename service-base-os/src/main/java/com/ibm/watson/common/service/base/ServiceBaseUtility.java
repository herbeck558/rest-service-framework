/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watson.common.service.base.security.XssCharEscapes;
import com.ibm.watson.service.base.model.ServiceError;

public class ServiceBaseUtility {
	public static final String SINGLE_TENANT = "single_tenant";
	private static final XssCharEscapes xssCharEscapes = new XssCharEscapes();
	private static final ObjectMapper mapperXssEscape = new ObjectMapper();
	static { mapperXssEscape.getFactory().setCharacterEscapes(xssCharEscapes); }

	public static Response buildApiOrParmNotAvailableResponse() {
		ServiceError se = new ServiceError().setCode(Status.NOT_FOUND.getStatusCode()).setMessage(Status.NOT_FOUND.getReasonPhrase())
				.setDescription("API or API parameter not available in current environment.");
		return Response.status(se.getCode()).type(MediaType.APPLICATION_JSON).entity(se).build();
	}

	public static void isDarkFeatureEnabled(String feature) {
		if (!ServiceContext.isDarkFeatureEnabled(feature)) {
			throw new WebApplicationException(ServiceBaseUtility.buildApiOrParmNotAvailableResponse());
		}
	}

	public static void checkForMultiTenantMode() {
		if (!ServiceContext.isDarkFeatureEnabled(SINGLE_TENANT)) {
			throw new WebApplicationException(ServiceBaseUtility.buildApiOrParmNotAvailableResponse());
		}
	}

	/**
	 * Validate the version parameter
	 *
	 * @param version
	 * @return null, if valid. Otherwise, return error Response object.
	 */
	public static Response apiSetup(String version, Logger logger, String methodName) {
		if (version == null) {
			// Initialize the service context
			ServiceContext sc = ServiceContext.createInstance();
			Boolean ignoreVersion = Boolean.valueOf(sc.getServiceProperties().getProperty("ignore_version"));
			if (ignoreVersion == null || !ignoreVersion) {
				return buildErrorResponse(Status.BAD_REQUEST, MediaType.APPLICATION_JSON_TYPE, "The version parameter is required");
			}
		} else {
			try {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				formatter.setLenient(false);
				VersionManager.setVersion(formatter.parse(version));
			} catch (ParseException e) {
				return buildErrorResponse(Status.BAD_REQUEST, MediaType.APPLICATION_JSON_TYPE, "Invalid version parameter value: " + version);
			}
		}

		return null;
	}

	/**
	 * Validate the version parameter
	 *
	 * @param version
	 * @return null, if valid. Otherwise, return error Response object.
	 */
	public static Response apiCleanup(Logger logger, String methodName) {
		try {
			VersionManager.setVersion(null);
		} catch (ParseException e) {
			return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, MediaType.APPLICATION_JSON_TYPE, "Exception while processing request: " + e.toString());
		}

		return null;
	}

	/**
	 * Build a jax-rs Response that includes an error message
	 *
	 * @param status
	 * @param mediaType
	 * @param error
	 *            error message
	 * @return jax-rs Response object
	 */
	public static Response buildErrorResponse(Status status, MediaType mediaType, String error) {
		if (mediaType == MediaType.TEXT_HTML_TYPE) {
			return Response.status(status).type(mediaType).entity(error).build();
		} else {
			ServiceError se = new ServiceError().setCode(status.getStatusCode()).setMessage(status.getReasonPhrase()).setDescription(error);
			try {
				return Response.status(se.getCode()).entity(mapperXssEscape.writeValueAsString(se)).build();
			} catch (JsonProcessingException e) {
				// Return non-escaped error response should ObjectMapper throw the above exception
				return Response.status(se.getCode()).entity(se).build();
			}
		}
	}

	/**
	 * Get thread correlation ID if present.  It will come from
	 * the log4j MDC object for now.  The plan is to get it from a common
	 * thread-local object in the future.
	 * @return Correlation ID if present or null if not.
	 */
	public static String getCorrelationId() {
		return (String) ServiceThreadLocal.getCorrelationId();
	}
  
  /**
   * Given a message, this method will return a timestamped version of it.
   * @param message
   * @return
   */
  public static String createTimestampedMessage(String message) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:S");
    String timestamp = sdf.format(new Date());
    return timestamp + ": " + message;
  }
  
  /**
   * Returns the debug setting for the current thread
   * @return
   */
  private static boolean isThreadDebugEnabled() {
    Boolean threadDebug = ServiceThreadLocal.getThreadDebug();
    if (threadDebug == null) {
      return false;
    }
    return threadDebug;
  }

}
