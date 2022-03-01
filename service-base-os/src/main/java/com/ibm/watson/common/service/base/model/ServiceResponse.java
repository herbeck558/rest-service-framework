/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base.model;

import java.io.Serializable;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Wrapper object for a REST response
 *
 */
//@XmlRootElement()
//@XmlAccessorType(XmlAccessType.PROPERTY)
//@XmlSeeAlso(ServiceStatus.class)
@ApiModel(value="service response", description="Object representing an HTTP Response")
public class ServiceResponse implements Serializable {

	private static final long serialVersionUID = 1;

	//@XmlElement(required=true)
	@ApiModelProperty(value="response code")
	private int code;
	@ApiModelProperty(value="Status of the response")
	//@XmlElement(required=true)
	private String reason;
	//@XmlElement(required=true)
	@ApiModelProperty(value="Response body")
	private Object payload;

	/**
	 * Default constructor
	 */
	public ServiceResponse() {

	}

	/**
	 * Convenience constructor with only status.
	 * @param status HTTP status
	 */
	public ServiceResponse(Response.Status status) {
		this(status, null);
	}

	/**
	 * Convenience constructor with status and a payload.
	 * @param status HTTP status
	 * @param payload Payload object or null if not provided.
	 */
	public ServiceResponse(Response.Status status, Object payload) {
		this(status, payload, null);
	}

	/** Convenience constructor with status, payload, and reason override.
	 *
	 * @param status HTTP status
	 * @param payload Payload object or null if not provided.
	 * @param statusReason Set status reason.  If null, reason defaults to status reason.
	 */
	public ServiceResponse(Response.Status status, Object payload, String statusReason) {
		code = status.getStatusCode();

		// Set status detail to code's reason if provided reason is null
		if(statusReason == null) {
			reason = status.getReasonPhrase();
		}
		else {
			// Set custom reason
			reason = statusReason;
		}

		this.payload = payload;
	}

	public ResponseBuilder createBuilder() {

		ResponseBuilder rb = Response.status(code);
//		rb.header("Access-Control-Allow-Origin", "*");  // TODO hack to get around browser cross domain issue.
		rb.entity(this);

		return rb;
	}


	public int getCode() {
		return code;
	}
	public void setCode(int code) {
		this.code = code;
	}

	public String getExtendedStatus() {
		return reason;
	}
	public void setExtendedStatus(String status) {
		this.reason = status;
	}

	public Object getPayload() {
		return payload;
	}
	public void setPayload(Object payload) {
		this.payload = payload;
	}

}
