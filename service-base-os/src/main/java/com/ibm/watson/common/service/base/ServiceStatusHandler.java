/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.watson.service.base.model.ServiceError;
import com.ibm.watson.service.base.model.ServiceStatus;
import com.ibm.watson.service.base.model.ServiceStatus.ServiceState;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * This class must be extended so that the @Path and @Api annotations get
 * set in the extended class instead of here.  This allows the version to be
 * set in the path and also prevents swagger from seeing a false resource if
 * the extended class' Path annotation is different than the base, which it
 * would typically be.
 *
 */
//@Path("status")  // Put this on the extended class with major version prefix
//@Api(value = "status")  // Put this on the extended class
//@SwaggerDefinition(tags={@Tag(name = "status", description = "Get the status of this service")})  // Put this on the extended class
public abstract class ServiceStatusHandler {
	private static final Logger logger = LoggerFactory.getLogger(ServiceStatusHandler.class.getName());

	protected static final long MEGABYTES = 1024*1024;
	public static final String PARAM_FORMAT = "format";
	public static final String PARM_LIVENESS_CHECK = "liveness_check";

	static {
		// Static initialization here...
		//logger.setLevel(Level.DEBUG);
	}

	/**
	 * Get the status of the serivce.
	 * The Datapower health check can use this to check service health.
	 * Datapower requires an XML response but cannot set the Media Type so the optional
	 * query parameter "format" can override the header format.
	 * @param format Format of the response data.  Overrides the header type.
	 * @return
	 */
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@ApiOperation(value = "Get status of service", response = ServiceStatus.class)
	public Response getServiceStatus(
			@QueryParam(PARAM_FORMAT)
			@DefaultValue("")
			@ApiParam(value = "Override response format", allowableValues="json, xml")
			String format,
			@QueryParam(PARM_LIVENESS_CHECK)
			@DefaultValue("false")
			@ApiParam(value = "Perform a shallow liveness check", allowableValues="true, false",
			  defaultValue="false")
			String liveness_check_parm
			) {
		ResponseBuilder serviceResponse;

		if(logger.isDebugEnabled()) logger.debug(">getServiceStatus()");

		try {
			// Process null query parameter(s)
			if(format == null) {
				format = "";
			}

			String buildTime = "0000/00/00.00.00.00";

			// TODO temp try until all services initialize the service context
			try {
				ServiceContext context = ServiceContext.getInstance();
				buildTime = context.getBuildTime();
			}
			catch (Exception e) {
				// Ignore for now
			}

			boolean liveness_check;
			switch(liveness_check_parm.toLowerCase()) {
			case "true":
				liveness_check = true;
				break;
			case "false":
				liveness_check = false;
				break;
			default:
				// Invalid parameter value
				logger.error("Unknown query parameter value '"+PARM_LIVENESS_CHECK+"="+liveness_check_parm+"'");

				ServiceError se = new ServiceError().
						setCode(Status.NOT_ACCEPTABLE.getStatusCode()).
						setMessage(Status.NOT_ACCEPTABLE.getReasonPhrase()).
						setDescription("Invalid value for query parameter '"+PARM_LIVENESS_CHECK+"', should be true or false");
				serviceResponse = Response.status(se.getCode()).entity(se);

				return serviceResponse.build();
				//break;
			}

			ServiceStatus serviceStatus = new ServiceStatus();
			serviceStatus.setVersion(buildTime);
			serviceStatus.setUpTime(getUpTime());

			serviceStatus.setRequestCount(ServiceContext.getRequestCount());

			String hostName;
			try {
				hostName = InetAddress.getLocalHost().getHostName();
			}
			catch(UnknownHostException e) {
				hostName = "unknown";
			}
			serviceStatus.setHostName(hostName);

			Runtime rt = Runtime.getRuntime();
			serviceStatus.setMaxMemoryMb(rt.maxMemory()/MEGABYTES);
			serviceStatus.setCommitedMemoryMb(rt.totalMemory()/MEGABYTES);
			serviceStatus.setInUseMemoryMb(rt.totalMemory()/MEGABYTES-rt.freeMemory()/MEGABYTES);
			int availableProcessors;
			try {
				availableProcessors = Runtime.getRuntime().availableProcessors();
			}
			catch(Exception e) {
				availableProcessors = 0;
			}
			serviceStatus.setAvailableProcessors(availableProcessors);

			// Concurrent limit related counters
			ServiceConcurrentLimit concurrentLimit = ServiceContext.getConcurrentLimit();
			if((concurrentLimit != null) && concurrentLimit.isConcurrentThresholdEnabled()) {
				serviceStatus.setConcurrentRequests(concurrentLimit.getConcurrentRequests());
				serviceStatus.setMaxConcurrentRequests(concurrentLimit.getMaxConcurrentRequests());
				serviceStatus.setTotalRejectedRequests(concurrentLimit.getTotalRejectedRequests());
				serviceStatus.setTotalBlockedRequests(concurrentLimit.getTotalBlockedRequests());
			}

			// Allow service status to be overridden by extended class
			if(liveness_check) {
				adjustServiceStatusLivenessCheck(serviceStatus);
			}
			else {
				adjustServiceStatus(serviceStatus);
			}

			serviceResponse = Response.ok(serviceStatus);

			switch(format) {
			case "json":
				serviceResponse.type(MediaType.APPLICATION_JSON);
				break;
			case "xml":
				serviceResponse.type(MediaType.APPLICATION_XML);
				break;
			default:
				// Ignore format override
				break;
			}

			// TODO Set pragma nocache?

			return serviceResponse.build();
		}
		finally {
			if(logger.isDebugEnabled()) logger.debug("<getServiceStatus()");
		}
	}

	@Path("health_check")
	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@ApiOperation(value = "Determine if service is running correctly",
		notes = "This resource differs from /status "+
			"in that it will will always return a 500 error if the service state is not OK.  This makes "+
			"it simpler for service front ends (such as Datapower) to detect a failed service.",
		response = ServiceStatus.class)
	@ApiResponses(value = {
		@ApiResponse(code = 500, message = "Service is not running properly", response=ServiceError.class)
	})
	public Response getHealthCheckStatus(
			@QueryParam(PARAM_FORMAT)
			@DefaultValue("")
			@ApiParam(value = "Override response format", allowableValues="json, xml")
			String format
			) {
		ResponseBuilder serviceResponse;

		if(logger.isDebugEnabled()) logger.debug(">getHealthCheckStatus()");

		try {

			// TODO temp try until all services initialize the service context
			try {
				ServiceContext context = ServiceContext.getInstance();
			}
			catch (Exception e) {
				// Ignore for now
			}

			ServiceStatus serviceStatus = new ServiceStatus();
			//serviceStatus.setVersion(buildTime);
			//serviceStatus.setUpTime(getUpTime());

			// Allow service status to be overridden by extended class
			adjustServiceStatus(serviceStatus);
			if(serviceStatus.getServiceState() == ServiceState.OK) {
				serviceResponse = Response.ok(serviceStatus);
			}
			else {
				ServiceError se = new ServiceError().
						setCode(Status.INTERNAL_SERVER_ERROR.getStatusCode()).
						setMessage(Status.INTERNAL_SERVER_ERROR.getReasonPhrase()).
						setDescription(serviceStatus.getStateDetails());
				serviceResponse = Response.status(se.getCode()).entity(se);
			}

			switch(format) {
				case "json":
					serviceResponse.type(MediaType.APPLICATION_JSON);
					break;
				case "xml":
					serviceResponse.type(MediaType.APPLICATION_XML);
					break;
				default:
					// Ignore format override
					break;
			}

			// TODO Set pragma nocache?

			return serviceResponse.build();

		}
		finally {
			if(logger.isDebugEnabled()) logger.debug("<getHealthCheckStatus()");
		}
	}

	/**
	 * Override this method to update the service status such serviceState and stateDetails
	 *
	 * @param status Status object to update
	 * @return
	 */
	protected ServiceStatus adjustServiceStatus(ServiceStatus status) {
		// Return error state if service initialization failed
		if(ServiceContext.getInstance().getInitializationFailed()) {
			status.setServiceState(ServiceState.ERROR);
			status.setStateDetails("Service initialization failed");
		}
		else {
			status.setServiceState(ServiceState.OK);
			status.setStateDetails("");
		}

		return status;
	}

	/**
	 * Override this method to update the service status for liveness checks such serviceState and stateDetails
	 *
	 * @param status Status object to update
	 * @return
	 */
	protected ServiceStatus adjustServiceStatusLivenessCheck(ServiceStatus status) {
		// Return error state if service initialization failed
		if(ServiceContext.getInstance().getInitializationFailed()) {
			status.setServiceState(ServiceState.ERROR);
			status.setStateDetails("Service initialization failed");
		}
		else {
			status.setServiceState(ServiceState.OK);
			status.setStateDetails("");
		}

		return status;
	}

	/**
	 * Get service up time as a formatted time string.
	 * @return Update time as a formatted string
	 */
	public static String getUpTime() {
		long upTime = System.currentTimeMillis()-ServiceBaseInitialization.getStartTime();
		long days = TimeUnit.MILLISECONDS.toDays(upTime);
		long hours = TimeUnit.MILLISECONDS.toHours(upTime) % 24;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(upTime) % 60;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(upTime) % 60;

		return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
	}


}

