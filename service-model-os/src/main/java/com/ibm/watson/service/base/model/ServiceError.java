/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.service.base.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * REST error response POJO
 *
 */
@XmlRootElement(name="serviceError")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(value="service error", description="Object representing an HTTP response with an error")
public class ServiceError {

	public static final String SERVICE_BASE_UTILITY_CLASS_NAME = "com.ibm.watson.common.service.base.ServiceBaseUtility";

	@XmlElement
	@ApiModelProperty(value="respone code")
	protected int code;
	@XmlElement
	@ApiModelProperty(value="response error message")
	protected String message;
	@XmlElement
	@ApiModelProperty(value="error severity level")
	protected ServiceErrorLevel level = ServiceErrorLevel.ERROR;
	@XmlElement
	@ApiModelProperty(value="error description")
	protected String description;
	@XmlElement
	@ApiModelProperty(value="additional error information")
	protected String moreInfo;
	@XmlElement
	@ApiModelProperty(value="error message correlation identifier")
	protected String correlationId;

	public enum ServiceErrorLevel {
		ERROR,
		WARNING,
		INFO
	}

	/**
	 * Default constructor
	 */
	public ServiceError() {
		initialize();
	}

	public ServiceError(int code) {
		initialize();
		this.code = code;
	}

	public ServiceError(int code, String message) {
		initialize();
		this.code = code;
		this.message = message;
	}

	/**
	 * Initialize this error object with default values from thread local storage
	 * if present. This is typically only used on the server side when the ServiceError
	 * object is being built, not inflated from json.
	 */
	protected void initialize() {
		// Get the correlation ID if the server-side utility class is present.
		// Reflection is used so that this class will compile on the client side
		// where the server-side utility class may not be on the classpath.
		try {
			Class<?> utilClass = Class.forName(SERVICE_BASE_UTILITY_CLASS_NAME);
			correlationId = (String) utilClass.getMethod("getCorrelationId").invoke(null, (Object[]) null);
		}
		catch(Exception e) {
			// Ignore error
		}
	}


	public int getCode() {
		return code;
	}
	public ServiceError setCode(int code) {
		this.code = code;
		return this;
	}

	public String getMessage() {
		return message;
	}

	public ServiceError setMessage(String message) {
		this.message = message;
		return this;
	}

	public ServiceErrorLevel getLevel() {
		return level;
	}

	public ServiceError setLevel(ServiceErrorLevel level) {
		this.level = level;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public ServiceError setDescription(String description) {
		this.description = description;
		return this;
	}

	public String getMoreInfo() {
		return moreInfo;
	}

	public ServiceError setMoreInfo(String moreInfo) {
		this.moreInfo = moreInfo;
		return this;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public ServiceError setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
		return this;
	}

}
