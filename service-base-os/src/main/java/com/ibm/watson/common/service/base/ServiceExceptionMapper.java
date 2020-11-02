/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ibm.watson.service.base.model.ServiceError;

/**
 * Catch all uncaught exceptions in jax-rs code so we can return a ServiceError json object instead of
 * an html page.
 *
 */
@Provider
public class ServiceExceptionMapper implements ExceptionMapper<Throwable>{
	private static final Logger logger = LoggerFactory.getLogger(ServiceExceptionMapper.class.getName());


	public Response toResponse(Throwable ex) {
		ServiceError se;

		try {
			//Throwable cause = ex.getCause();
			Throwable exClean = ex;

			// A WebApplicatonException object should have the response set but some jax-rs
			// code must not do this if the exception was thrown with only the status
			// set.  To prevent this method from converting the status code to a 500 error, set the ServiceError
			// object here with the error status.
			if(ex instanceof WebApplicationException) {
				WebApplicationException wae = (WebApplicationException) ex;
				int code = wae.getResponse().getStatus();
				Status status = Response.Status.fromStatusCode(code);
				String reason = "";
				if(status != null) {
					reason = status.getReasonPhrase();
				}
				se = new ServiceError(code,	reason);
				se.setDescription("Reason: Uncaught exception");
			}
			else if (ex instanceof JsonParseException) {
				// drop location / cause portion of message that may contain user text input
				JsonParseException jpe =  new JsonParseException(((JsonProcessingException) ex).getOriginalMessage(), null);
				exClean = jpe;
				se = new ServiceError(Status.BAD_REQUEST.getStatusCode(), Status.BAD_REQUEST.getReasonPhrase());
				se.setDescription("Reason: JSON Parsing Error");
			}
			else if (ex instanceof JsonProcessingException){
				//JsonProcessingException jpe  = (JsonProcessingException) ex;
				//exceptionSummary = jpe.getOriginalMessage() != null && !jpe.getOriginalMessage().isEmpty() ? jpe.getOriginalMessage() : exceptionSummary;
				se = new ServiceError(Status.BAD_REQUEST.getStatusCode(), Status.BAD_REQUEST.getReasonPhrase());
				se.setDescription("Reason: JSON Processing Error");
			}
			else {
				// Return a 500 error for all other exceptions that get here
				se = new ServiceError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
						Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());

				// TODO temp code
				// If exception is an NPE and occurred in PerRequestResourceProvider.releaseInstance,
				// treat this as an initialization error.  There appears to be a bug in jax-rs 2.0 code
				// when an exception is thrown during handler or filter initialization.  The real reason
				// is masked by a second exception in PerRequestResourceProvider.releaseInstance().
				// Until this problem is fixed, we will log an error indicating the request failed
				// most likely caused by an initialization error.
				String npeReason;
				String topOfStack = "";
				try {
					topOfStack = ex.getStackTrace()[0].toString();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(ex instanceof NullPointerException &&
						topOfStack.contains("PerRequestResourceProvider.releaseInstance")) {
					npeReason = "Reason: Request failed because service did not start properly";
				}
				else {
					npeReason = "Reason: Uncaught exception";
				}

				se.setDescription(npeReason);
			}

			logger.error("HTTP Status: "+se.getCode()+", "+se.getDescription(), exClean);
		}
		catch(Throwable nestedEx) {
			// This should not really occur unless there is a bug in this code.
			// Build a 500 ServiceError with some detail
			// String nestedException = nestedEx.toString()+" at "+nestedEx.getStackTrace()[0].toString();
			se = new ServiceError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
			Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
			se.setDescription("Reason: Uncaught nested exception");

			logger.error("HTTP Status: "+se.getCode()+", Nested Exception", nestedEx);
			logger.error("Original Exception", ex);

		}

		ResponseBuilder rb = Response.status(se.getCode()).
				entity(se).
				type(MediaType.APPLICATION_JSON);

		return rb.build();
	}

}
