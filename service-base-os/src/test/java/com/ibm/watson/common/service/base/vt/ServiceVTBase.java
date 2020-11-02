/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.vt;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

import org.apache.http.HttpStatus;
import org.hamcrest.core.StringContains;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.ibm.watson.common.service.base.ServiceStatusHandler;
import com.ibm.watson.common.service.base.utilities.BVT;
import com.ibm.watson.common.service.base.utilities.DVT;
import com.ibm.watson.common.service.base.utilities.MVT;
import com.ibm.watson.common.service.base.utilities.ServiceVTUtils;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;

public abstract class ServiceVTBase extends ServiceVTUtils {

	/**
	 * Test non secure port
	 */
	@Category(MVT.class)
	@Test
	public void testHttp() {
		Assume.assumeTrue(isHttpActive());
		final String resource = getHttpUrlBase() + STATUS_HEALTH_CHECK_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_XML);
		request.queryParameter(ServiceStatusHandler.PARAM_FORMAT, "xml");
		runSuccessValidation(request.get(resource).then(), ContentType.XML, HttpStatus.SC_OK)
				.body("serviceStatus.serviceState", equalTo("OK"));
	}

	/**
	 * Test secure port
	 */
	@Category(MVT.class)
	@Test
	public void testHttps() {
		Assume.assumeTrue(isHttpsActive());
		final String resource = getHttpsUrlBase() + STATUS_HEALTH_CHECK_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_XML);
		request.queryParameter(ServiceStatusHandler.PARAM_FORMAT, "xml");
		runSuccessValidation(request.get(resource).then(), ContentType.XML, HttpStatus.SC_OK)
				.body("serviceStatus.serviceState", equalTo("OK"));
	}

	/**
	 * Common status tester for JSON output
	 */
	@Category(BVT.class)
	@Test
	public void testStatusJson() {
		final String resource = getUrlBase() + STATUS_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_JSON);

		if (!getServiceAuthApikey().isEmpty()) {
			runErrorValidation(request.get(resource).then().log().all(), ContentType.JSON, HttpStatus.SC_NOT_FOUND);
		} else {
			runSuccessValidation(request.get(resource).then(), ContentType.JSON, HttpStatus.SC_OK)
					.body("serviceState", equalTo("OK")).body("requestCount", greaterThan(0))
					.body("hostName", not(equalTo(""))).body("maxMemoryMb", greaterThan(0))
					.body("commitedMemoryMb", greaterThan(0)).body("inUseMemoryMb", greaterThan(0))
					.body("availableProcessors", greaterThan(0));
		}
	}

	/**
	 * Common status tester for XML output
	 */
	@Category(BVT.class)
	@Test
	public void testStatusXml() {
		final String resource = getUrlBase() + STATUS_PATH;
		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_XML);

		if (!getServiceAuthApikey().isEmpty()) {
			runErrorValidation(request.get(resource).then().log().all(), ContentType.JSON, HttpStatus.SC_NOT_FOUND);
		} else {
			runSuccessValidation(request.get(resource).then(), ContentType.XML, HttpStatus.SC_OK)
					.body("serviceStatus.serviceState", equalTo("OK")).
					// Note: The body method uses groovy to get the item
					body("serviceStatus.requestCount.toLong()", greaterThan(0L))
					.body("serviceStatus.hostName", not(equalTo("")))
					.body("serviceStatus.maxMemoryMb.toLong()", greaterThan(0L))
					.body("serviceStatus.commitedMemoryMb.toLong()", greaterThan(0L))
					.body("serviceStatus.inUseMemoryMb.toLong()", greaterThan(0L))
					.body("serviceStatus.availableProcessors.toLong()", greaterThan(0L));
		}
	}

	/**
	 * Common health check status tester for JSON output
	 */
	@Category(BVT.class)
	@Test
	public void testHealthCheckJson() {
		final String resource = getUrlBase() + STATUS_HEALTH_CHECK_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_JSON);
		runSuccessValidation(request.get(resource).then(), ContentType.JSON, HttpStatus.SC_OK).body("serviceState",
				equalTo("OK"));
	}

	/**
	 * Common health check status tester for XML output
	 */
	@Category(MVT.class) // to tag a specific test to be part of MVT (minimum verification test - used in
							// IBM Cloud Toolchain)
	@Test
	public void testHealthCheckXml() {
		final String resource = getUrlBase() + STATUS_HEALTH_CHECK_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_XML);
		runSuccessValidation(request.get(resource).then(), ContentType.XML, HttpStatus.SC_OK)
				.body("serviceStatus.serviceState", equalTo("OK"));
	}

	/**
	 * Common health check status tester for XML output
	 */
	@Category(DVT.class) // to tag a specific test to be part of DVT (deployment verification test)
	@Test
	public void testHealthCheckForceXml() {
		final String resource = getUrlBase() + STATUS_HEALTH_CHECK_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_XML);
		request.queryParameter(ServiceStatusHandler.PARAM_FORMAT, "xml");
		runSuccessValidation(request.get(resource).then(), ContentType.XML, HttpStatus.SC_OK)
				.body("serviceStatus.serviceState", equalTo("OK"));
	}

	/**
	 * Common swagger tester
	 */
	@Category(BVT.class)
	@Test
	public void testSwagger() {
		final String resource = getUrlBase() + SWAGGER_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_STANDARD_JSON);

		if (!getServiceAuthApikey().isEmpty()) {
			runErrorValidation(request.get(resource).then().log().all(), ContentType.JSON, HttpStatus.SC_NOT_FOUND);
		} else {
			runSuccessValidation(request.get(resource).then(), ContentType.JSON, HttpStatus.SC_OK)
					.body("swagger", equalTo("2.0")).body("info.version", not(equalTo("")))
					.body("basePath", equalTo(getApiRoot()));
		}

	}

	/**
	 * Common swagger UI tester, make sure swagger UI index file is present.
	 */
	@Category(DVT.class)
	@Test
	public void testSwaggerUI() {
		final String resource = getUrlRoot() + SWAGGER_UI_PATH;

		RequestSpecification request = buildBaseRequest(HEADER_IDENTITY_HTML);
		if (!getServiceAuthApikey().isEmpty()) {
			runErrorValidation(request.get(resource).then().log().all(), ContentType.JSON, HttpStatus.SC_NOT_FOUND);
		} else {
			runSuccessValidation(request.get(resource).then().log().all(), ContentType.HTML, HttpStatus.SC_OK)
					.content(StringContains.containsString("<html>"));
		}
	}

}
